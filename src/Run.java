import java.io.BufferedReader;
import java.io.FileReader;

public class Run {
    public static void main(String[] args) {
        String name = "test";
        String src = "";
        try {
            BufferedReader br = new BufferedReader(new FileReader(name));
            for (String line; (line = br.readLine()) != null; src += line + "\n");
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
        MetaAnalyzer analysis = new MetaAnalyzer(src);


    }
}
