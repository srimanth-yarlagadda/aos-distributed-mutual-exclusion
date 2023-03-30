default: clean
	clear
	javac Server.java
	java Server

clean:
	rm -rf Server\$*.class
	rm -rf Server.class

c:
	clear
	javac ClientHelper.java Client.java

micro: c
	java Client

milli: c
	java Client 1000

seconds: c
	java Client 1000000