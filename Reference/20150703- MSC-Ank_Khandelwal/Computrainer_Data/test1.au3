#include <Constants.au3>

Run("C:\Program Files (x86)\CompCS\cchart.exe")
Sleep(500)
Send("!f")
Sleep(500)
Send("{RIGHT 1}{ENTER}")
Sleep(500)
;Send("CompuTrainer 3D V3{ENTER}")
;Sleep(500)
;Send("Rider Performance{Enter}")
;Sleep(500)
Send("lastperf.3dp{Enter}")
Sleep(500)
Send("{ENTER}")
Sleep(500)
Send("!{F4}")
Sleep(500)
WinWaitClose("[CLASS:CompCS]")