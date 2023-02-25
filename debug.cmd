rm debug.log

if [%1]==[] goto :blank
gradlew debug --args="%*"
exit

:blank
gradlew debug