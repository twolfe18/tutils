
jar:
	rm -f target/*.jar
	mvn compile assembly:single -DskipTests

