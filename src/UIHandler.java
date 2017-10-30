import java.io.File;
import java.util.Collection;

public class UIHandler {
    public static File askUserForFile() {
        File myfile = new File("src/MetaAnalyzer.java");
        return myfile;
    }

    /**
     * @param s string to pad
     * @param length length to pad to
     * @return string with enough " " added onto the end to make it reach the desired length
     */
    public static String rpad(String s, int length) {
        StringBuilder sBuilder = new StringBuilder(s);
        while (sBuilder.length() < length) {
            sBuilder.append(" ");
        }
        return sBuilder.toString();
    }

    /**
     * @param s converts to string, pads that
     * @param length length to pad to
     * @param <T> type of Object to pad
     * @return string with enough " " added onto the end to make it reach the desired length
     */
    public static <T> String rpad(T s, int length) {
        return rpad(s.toString(), length);
    }

    /**
     * prints collection with some formatting
     *
     * @param vec vector to print out
     * @param msg message text to print before, with all {} replaced with the size of vec
     * @param <T> type of object vec holds
     */
    public static <T> void printCollection(Collection<T> vec, String msg) {
        final int vsize = vec.size();
        System.out.println(msg.replaceAll("\\{}", Integer.toString(vsize)));
        for (T item : vec) {
            System.out.println(item.toString());
        }
    }


}
