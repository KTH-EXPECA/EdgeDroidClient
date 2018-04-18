# Trace replay client for Gabriel

Instructions:
1. Set up a local NTP server on the backend. Configure it by modifying `/etc/ntp.conf`:
	a. Allow local devices to synchronize to it by adding the line `restrict 192.168.0.0 mask 255.255.255.0 nomodify notrap`. Replace the network IP address and netmask to match your configuration.
	b. Add the following lines to allow it to provide time to the network even if not connected to the internet
	```
	server 127.127.1.0
	fudge 127.127.1.0 stratum 10
	```
	c. For faster sync, modify your server lines to look like `server <server_address> minpoll 3 maxpoll 4`, and add `tinker step 0.010` to the end of the file.
2. Set up and run Gabriel with the LEGO application.
3. Ensure your firewall is not blocking ports (in Ubuntu: `sudo ufw disable`).
4. Import the project into Android Studio and compile it. It should automatically download dependencies.
5. Install the APK into your Android device.
6. Get the step traces from the CognitiveAssistanceTraces repository (https://github.com/molguin92/CognitiveAssistanceTraces), and put them into a separate folder on your device, like:
```
/sdcard/trace/
|- step_1.trace
|- step_2.trace
|- step_3.trace
.
.
.
|- step_7.trace
```
7. Open the application, select the trace folder and press connect.
8. On first run, the application will force an NTP synchronization with the backend, which make take some time. Also, if the backend has too much dispersion this step will probably fail, so check that dispersion is under 10 ms before running the application by executing `$ netstat` on the backend.
