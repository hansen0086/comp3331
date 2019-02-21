import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * log class
 */

public class senderLog {
    private File file;

    public senderLog(String filePathName) throws IOException {
        file = new File(filePathName);
        file.createNewFile();
    }


    public void sendInfo(String content) throws IOException {
        content = content + "\n";
        FileWriter fw = new FileWriter(file, true);
        fw.write(content);
        fw.close();
    }


    public void sendInfo(String str, int i) throws IOException {
        String content = "";
        content = content + str + "                 " + i;
        sendInfo(content);
    }

    public void sendInfo(String eventType, long startTime, String packetType, int seq, int len, int ack) throws IOException {
        long currentTime = System.currentTimeMillis() - startTime;
        String result = "" + String.format("%.2f", (double)currentTime / 1000);
        sendInfo(eventType, result, packetType, seq + "  ", len + " ", ack + "");
    }


    public void sendInfo(String... list) throws IOException {
        String content = "";
        int len = list.length;
        for (int i = 0; i < len; i++) {
            String str = list[i] + "        ";
            content = content + str;
        }
        sendInfo(content);
    }

}
