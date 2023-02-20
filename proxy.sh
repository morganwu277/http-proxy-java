if [ $# -eq 0 ]
  then
    gradlew run
  else
    gradlew run --args="$*" $JAVA_OPTS
fi
