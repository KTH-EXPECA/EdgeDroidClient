# EdgeDroid client emulator

Instructions:

1. Import project into Android Studio.

2. Build the project using Gradle. It should work out of the box.

3. Set up the Backend.

4. Modify `se/kth/molguin/tracedemo/network/control/ControlConst.java` so that the `SERVER` field points to the cloudlet IP address where the backend will be running.

```java
...
// server IP
public static final String SERVER = "123.456.789.123";  // Cloudlet
...
```
5. Compile and install on the Android devices.

6. Run.
