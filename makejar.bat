javac -g --module-path /Users/enriqueoti/javafx-sdk-11.0.2/lib --add-modules=javafx.controls JavaRace.java *.java
jar -cmf mani.txt JavaRace.jar JavaRace*.class *.png settings.xml
jar -tvf JavaRace.jar
java --module-path /Users/enriqueoti/javafx-sdk-11.0.2/lib --add-modules=javafx.controls -jar JavaRace.jar
pause