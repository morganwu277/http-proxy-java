if [%1]==[] goto :blank
gradlew run --args="%*"
exit

:blank
gradlew run