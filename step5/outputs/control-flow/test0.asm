; Symbol table GLOBAL
; name true type STRING location 0x10000000 value "True\n"
; name false type STRING location 0x10000004 value "False\n"
; name a type FLOAT location 0x20000000
; name b type FLOAT location 0x20000004
; Function: INT main([])

; Symbol table main

.section .text
;Current temp: null
;IR Code: 
MV fp, sp
JR func_main
HALT
;
;
;Function: main
func_main:
SW fp, 0(sp)
MV fp, sp
ADDI sp, sp, -4
ADDI sp, sp, -0
;Saving registers
SW x4, 0(sp)
ADDI sp, sp, -4
SW x5, 0(sp)
ADDI sp, sp, -4
FSW f0, 0(sp)
ADDI sp, sp, -4
FSW f1, 0(sp)
ADDI sp, sp, -4
FSW f2, 0(sp)
ADDI sp, sp, -4
FSW f3, 0(sp)
ADDI sp, sp, -4
;
;Start of BB
FIMM.S f0, 3.0
FMV.S f1, f0
FIMM.S f2, 2.0
FMV.S f3, f2
FLT.S x4, f1, f3
;Saving registers at end of BB
LA x3, 0x20000000
FSW f1, 0(x3)
LA x3, 0x20000004
FSW f3, 0(x3)
BNE x4, x0, else_1
;End of BB
;Start of BB
LA x3, 0x10000000
PUTS x3
;Saving registers at end of BB
J out_1
;End of BB
;Start of BB
else_1:
LA x3, 0x10000004
PUTS x3
;Saving registers at end of BB
;End of BB
;Start of BB
out_1:
LI x4, 0
MV x5, x4
;Saving registers at end of BB
SW x5, 8(fp)
J func_ret_main
;End of BB
;
func_ret_main:
;Restore registers
ADDI sp, sp, 4
FLW f3, 0(sp)
ADDI sp, sp, 4
FLW f2, 0(sp)
ADDI sp, sp, 4
FLW f1, 0(sp)
ADDI sp, sp, 4
FLW f0, 0(sp)
ADDI sp, sp, 4
LW x5, 0(sp)
ADDI sp, sp, 4
LW x4, 0(sp)
MV sp, fp
LW fp, 0(fp)
RET
;End of function main
;


.section .strings
0x10000000 "True\n"
0x10000004 "False\n"
