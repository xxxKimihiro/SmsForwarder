package tsnetbind

import (
	"context"
	"encoding/binary"
	"fmt"
	"io"
	"net"
	"strconv"
	"sync"
	"time"

	"tailscale.com/tsnet"
)

var (
	mu        sync.Mutex
	srv       *tsnet.Server
	socksLn   net.Listener
	tailLn    net.Listener
	running   bool
	selfIP    string
	socksPort int
	lastErr   string
)

// Start joins the tailnet in userspace (no TUN / no system VPN).
// authKey may be empty after the first successful login (state lives in stateDir).
func Start(stateDir, authKey, hostname string, advertisePort, localPort int) error {
	mu.Lock()
	defer mu.Unlock()
	if running {
		return nil
	}
	lastErr = ""
	if stateDir == "" {
		return setErr(fmt.Errorf("stateDir required"))
	}
	if hostname == "" {
		hostname = "kosync"
	}
	if advertisePort <= 0 {
		advertisePort = 5000
	}
	if localPort <= 0 {
		localPort = 5000
	}

	s := &tsnet.Server{
		Dir:       stateDir,
		Hostname:  hostname,
		AuthKey:   authKey,
		Ephemeral: false,
	}
	ctx, cancel := context.WithTimeout(context.Background(), 45*time.Second)
	defer cancel()
	status, err := s.Up(ctx)
	if err != nil {
		_ = s.Close()
		return setErr(fmt.Errorf("tsnet up: %w", err))
	}
	ip := ""
	if status != nil {
		if addr := status.TailscaleIPs; len(addr) > 0 {
			ip = addr[0].String()
		}
	}

	sln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		_ = s.Close()
		return setErr(fmt.Errorf("socks listen: %w", err))
	}
	tln, err := s.Listen("tcp", ":"+strconv.Itoa(advertisePort))
	if err != nil {
		_ = sln.Close()
		_ = s.Close()
		return setErr(fmt.Errorf("tailnet listen :%d: %w", advertisePort, err))
	}

	srv = s
	socksLn = sln
	tailLn = tln
	socksPort = sln.Addr().(*net.TCPAddr).Port
	selfIP = ip
	running = true

	go acceptSOCKS(s, sln)
	go acceptForward(tln, "127.0.0.1:"+strconv.Itoa(localPort))
	return nil
}

// Stop closes the embedded node. Saved state in stateDir is kept.
func Stop() {
	mu.Lock()
	defer mu.Unlock()
	stopLocked()
}

func stopLocked() {
	running = false
	if socksLn != nil {
		_ = socksLn.Close()
		socksLn = nil
	}
	if tailLn != nil {
		_ = tailLn.Close()
		tailLn = nil
	}
	if srv != nil {
		_ = srv.Close()
		srv = nil
	}
	socksPort = 0
	selfIP = ""
}

// Running reports whether the userspace node is up.
func Running() bool {
	mu.Lock()
	defer mu.Unlock()
	return running
}

// SelfIP is the node's Tailscale IPv4 (or IPv6 if no v4), empty if not up.
func SelfIP() string {
	mu.Lock()
	defer mu.Unlock()
	return selfIP
}

// SocksPort is the local SOCKS5 port on 127.0.0.1, or 0.
func SocksPort() int {
	mu.Lock()
	defer mu.Unlock()
	return socksPort
}

// LastError is the last Start failure, empty if none.
func LastError() string {
	mu.Lock()
	defer mu.Unlock()
	return lastErr
}

func setErr(err error) error {
	lastErr = err.Error()
	return err
}

func acceptSOCKS(s *tsnet.Server, ln net.Listener) {
	for {
		c, err := ln.Accept()
		if err != nil {
			return
		}
		go handleSOCKS(s, c)
	}
}

func acceptForward(ln net.Listener, local string) {
	for {
		c, err := ln.Accept()
		if err != nil {
			return
		}
		go func(src net.Conn) {
			dst, err := net.DialTimeout("tcp", local, 5*time.Second)
			if err != nil {
				_ = src.Close()
				return
			}
			pipe(src, dst)
		}(c)
	}
}

func handleSOCKS(s *tsnet.Server, c net.Conn) {
	defer c.Close()
	_ = c.SetDeadline(time.Now().Add(15 * time.Second))
	hdr := make([]byte, 2)
	if _, err := io.ReadFull(c, hdr); err != nil || hdr[0] != 5 {
		return
	}
	nMethods := int(hdr[1])
	if nMethods <= 0 {
		return
	}
	if _, err := io.ReadFull(c, make([]byte, nMethods)); err != nil {
		return
	}
	if _, err := c.Write([]byte{5, 0}); err != nil {
		return
	}
	req := make([]byte, 4)
	if _, err := io.ReadFull(c, req); err != nil || req[0] != 5 || req[1] != 1 {
		return
	}
	host, err := readSOCKSAddr(c, req[3])
	if err != nil {
		return
	}
	portBuf := make([]byte, 2)
	if _, err := io.ReadFull(c, portBuf); err != nil {
		return
	}
	port := binary.BigEndian.Uint16(portBuf)
	_ = c.SetDeadline(time.Time{})
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	dst, err := s.Dial(ctx, "tcp", net.JoinHostPort(host, strconv.Itoa(int(port))))
	cancel()
	if err != nil {
		_, _ = c.Write([]byte{5, 1, 0, 1, 0, 0, 0, 0, 0, 0})
		return
	}
	if _, err := c.Write([]byte{5, 0, 0, 1, 0, 0, 0, 0, 0, 0}); err != nil {
		_ = dst.Close()
		return
	}
	pipe(c, dst)
}

func readSOCKSAddr(c net.Conn, atyp byte) (string, error) {
	switch atyp {
	case 1:
		buf := make([]byte, 4)
		if _, err := io.ReadFull(c, buf); err != nil {
			return "", err
		}
		return net.IP(buf).String(), nil
	case 3:
		l := make([]byte, 1)
		if _, err := io.ReadFull(c, l); err != nil {
			return "", err
		}
		buf := make([]byte, int(l[0]))
		if _, err := io.ReadFull(c, buf); err != nil {
			return "", err
		}
		return string(buf), nil
	case 4:
		buf := make([]byte, 16)
		if _, err := io.ReadFull(c, buf); err != nil {
			return "", err
		}
		return net.IP(buf).String(), nil
	default:
		return "", fmt.Errorf("bad atyp %d", atyp)
	}
}

func pipe(a, b net.Conn) {
	defer a.Close()
	defer b.Close()
	done := make(chan struct{}, 2)
	go func() {
		_, _ = io.Copy(a, b)
		done <- struct{}{}
	}()
	go func() {
		_, _ = io.Copy(b, a)
		done <- struct{}{}
	}()
	<-done
}
