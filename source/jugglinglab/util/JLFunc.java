// JLFunc.java
//
// Copyright 2019 by Jack Boyce (jboyce@gmail.com)

package jugglinglab.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


// Some useful functions

public class JLFunc {
    // Binomial coefficient (a choose b)
    public static int binomial(int a, int b) {
        int result = 1;

        for (int i = 0; i < b; i++) {
            result *= (a - i);
            result /= (i + 1);
        }

        return result;
    }

    // Throughout Juggling Lab we use a notation within strings to indicate
    // repeated sections:  `...(stuff)^n...`. This function expands all such
    // repeats (including nested ones) to produce a fully-expanded string.
    //
    // NOTE: A limitation is that if `stuff` contains parentheses they must
    // be balanced. Otherwise we get ambiguous cases like '(()^5' --> does this
    // expand to '(((((' or '('?
    public static String expandRepeats(String str) {
        StringBuffer sb = new StringBuffer();
        addExpansionToBuffer(str, sb);
        return sb.toString();

        /*
        System.out.println(JLFunc.expandRepeats("hello"));
        System.out.println(JLFunc.expandRepeats("he(l)^2o"));
        System.out.println(JLFunc.expandRepeats("hel(lo)^2"));
        System.out.println(JLFunc.expandRepeats("(hello)^0world"));
        System.out.println(JLFunc.expandRepeats("((hello )^2there)^2"));
        System.out.println(JLFunc.expandRepeats("((hello )there)^2"));
        System.out.println(JLFunc.expandRepeats("((hello )^2there)"));
        */
    }

    protected static void addExpansionToBuffer(String str, StringBuffer sb) {
        for (int pos = 0; pos < str.length(); ) {
            char ch = str.charAt(pos);

            if (ch == '(') {
                int[] result = tryParseRepeat(str, pos);

                if (result == null) {
                    // no repeat found, treat like a normal character
                    sb.append(ch);
                    pos++;
                } else {
                    int repeat_end = result[0];
                    int repeats = result[1];
                    int resume_start = result[2];

                    // snip out the string to be repeated:
                    String str2 = str.substring(pos + 1, repeat_end);

                    for (int i = 0; i < repeats; i++)
                        addExpansionToBuffer(str2, sb);

                    pos = resume_start;
                }
            } else {
                sb.append(ch);
                pos++;
            }
        }
    }

    // Scan forward in the string to find:
    // (1) the end of the repeat (buffer position of ')' where depth returns to 0)
    // (2) the number of repeats
    //     - if the next non-whitespace char after (a) is not '^' -> no repeat
    //     - if the next non-whitespace char after '^' is not a number -> no repeat
    //     - parse the numbers after '^' up through the first non-number (or end
    //       of string) into an int = `repeats`
    // (3) the buffer position of the first non-numeric character after the
    //     repeat number (i.e. where to resume) = `resume_start`
    //     (=str.length() if hit end of buffer)
    //
    // We always call this function with `fromPos` sitting on the '(' that starts
    // the repeat section.
    protected static int[] tryParseRepeat(String str, int fromPos) {
        int depth = 0;

        for (int pos = fromPos; pos < str.length(); pos++) {
            char ch = str.charAt(pos);

            if (ch == '(')
                depth++;
            else if (ch == ')') {
                depth--;
                if (depth == 0) {
                    // see if we match the form '^(int)...' after the closing
                    // parenthesis
                    Pattern pat = Pattern.compile("^\\s*\\^\\s*(\\d+).*");
                    Matcher m = pat.matcher(str.substring(pos + 1, str.length()));

                    if (!m.matches())
                        return null;

                    int repeat_end = pos;
                    int repeats = Integer.parseInt(m.group(1));
                    int resume_start = m.end(1) + pos + 1;

                    int[] result = new int[3];
                    result[0] = repeat_end;
                    result[1] = repeats;
                    result[2] = resume_start;
                    return result;
                }
            }
        }
        return null;
    }

    // convert a double value to a String, with trailing '.' and '0's suppressed
    public static String toStringTruncated(double val, int digits) {
        String result = Double.toString(val);

        if (result.indexOf('E') > 0) {
            if (val < 0.0) {
                val = -val;
                result = "-";
            } else
                result = "";

            result += Integer.toString((int)val);
            if (digits < 1)
                return result;
            result += ".";
            val -= (double)((int)val);
            for (int i = 0; i < digits; i++)
                val *= 10.0;
            String rem = Integer.toString((int)val);
            for (int i = rem.length(); i < digits; i++)
                result += "0";
            result += rem;
        }

        int decimalpos = result.indexOf('.');
        if (decimalpos < 0)
            return result;

        int endpos = decimalpos + digits + 1;
        if (endpos > result.length())
            endpos = result.length();

        while (endpos > 0) {
            int ch = result.charAt(endpos-1);

            if (ch == '.') {
                endpos--;
                break;
            }
            else if (ch == '0') {
                endpos--;
            }
            else
                break;
        }

        return result.substring(0, endpos);
    }
}
