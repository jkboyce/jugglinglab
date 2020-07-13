
Optimizer how-to on Linux
-------------------------

Beginning with Juggling Lab version 1.3, there is a feature called the
optimizer that requires some additional components to work.

This optimizer feature is optional, and if the additional components aren't
present then Juggling Lab functions fine with that feature turned off.


Here are steps to install the needed components for your Linux distro:

1. Obtain a binary image of Google's OR-Tools for Java:

   a. navigate to https://developers.google.com/optimization/install/java
   b. select "Binary installation on Linux"
   c. download the correct distribution for your system (it is NOT
      necessary to download the FlatZinc version)
      (If your system isn't present then you could attempt to build OR-Tools
      from source. Not for the faint of heart.)
   d. extract the download archive into any convenient location

2. Validate your installation as described at the bottom of the download page

3. Copy the /lib directory inside OR-Tools into the Juggling Lab directory,
   next to JugglingLab.jar, and rename it /ortools-lib
   (The rest of the OR-Tools installation can be deleted.)

4. There should now be a directory /ortools-lib in the same location as
   JugglingLab.jar

5. Start Juggling Lab with the provided `JugglingLab.sh` script.

--->

If the OR-Tools are installed correctly, the File-->Optimize menu option
should become available on all pattern windows.
