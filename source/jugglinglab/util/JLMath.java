// JLMath.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.util;


// A few math functions

public class JLMath {
    public static final double pi = 3.141592653589793238;

    public static double toRad(double deg) {
        return (deg * pi / 180.0);
    }

    // a choose b
    public static int choose(int a, int b) {
        int result = 1;

        for (int i = 0; i < b; i++) {
            result *= (a - i);
            result /= (i + 1);
        }

        return result;
    }
}

