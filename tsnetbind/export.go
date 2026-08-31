package main

/*
#include <stdlib.h>
*/
import "C"

//export GoTsnetStart
func GoTsnetStart(stateDir, authKey, hostname *C.char, advertisePort, localPort C.longlong) *C.char {
	err := Start(C.GoString(stateDir), C.GoString(authKey), C.GoString(hostname), int(advertisePort), int(localPort))
	if err != nil {
		return C.CString(err.Error())
	}
	return nil
}

//export GoTsnetStop
func GoTsnetStop() {
	Stop()
}

//export GoTsnetRunning
func GoTsnetRunning() C.int {
	if Running() {
		return 1
	}
	return 0
}

//export GoTsnetSelfIP
func GoTsnetSelfIP() *C.char {
	return C.CString(SelfIP())
}

//export GoTsnetSocksPort
func GoTsnetSocksPort() C.longlong {
	return C.longlong(SocksPort())
}

//export GoTsnetLastError
func GoTsnetLastError() *C.char {
	return C.CString(LastError())
}

func main() {}
