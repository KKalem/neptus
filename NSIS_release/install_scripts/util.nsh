#############################################################################
# Copyright (c) 2004-2013 Laboratório de Sistemas e Tecnologia Subaquática  #
# Departamento de Engenharia Electrotécnica e de Computadores               #
# Rua Dr. Roberto Frias, 4200-465 Porto, Portugal                           #
#############################################################################
# Author: Paulo Dias                                                        #
#############################################################################
# This script is the NSIS Utils for Neptus installer                        #
#############################################################################

Function WriteToFile
  Exch $0 ;file to write to
  Exch
  Exch $1 ;text to write
  
  FileOpen $0 $0 a #open file
  FileSeek $0 0 END #go to end
  FileWrite $0 $1 #write to file
  FileClose $0
  
  Pop $1
  Pop $0
FunctionEnd

!macro WriteToFile NewLine File String
  !if `${NewLine}` == true
  Push `${String}$\r$\n`
  !else
  Push `${String}`
  !endif
  Push `${File}`
  Call WriteToFile
!macroend
!define WriteToFile '!insertmacro WriteToFile false'
!define WriteLineToFile '!insertmacro WriteToFile true'

!macro WriteLangPref String
  CreateDirectory "$INSTDIR\conf"
  ${WriteLineToFile} "$INSTDIR\conf\general-properties.xml" '<?xml version="1.0" encoding="UTF-8"?>'
  ${WriteLineToFile} "$INSTDIR\conf\general-properties.xml" '<!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd">'
  ${WriteLineToFile} "$INSTDIR\conf\general-properties.xml" '<properties>'
  ${WriteLineToFile} "$INSTDIR\conf\general-properties.xml" '<comment>Properties generated by Neptus</comment>'
  ${WriteLineToFile} "$INSTDIR\conf\general-properties.xml" '<entry key="Language">${String}</entry>'
  ${WriteLineToFile} "$INSTDIR\conf\general-properties.xml" '</properties>'
!macroend
!define WriteLangPref '!insertmacro WriteLangPref'
