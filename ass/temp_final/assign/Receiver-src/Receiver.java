import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.*;
import java.util.*;

import static java.lang.reflect.Array.getByte;

public class Receiver {
    public DatagramSocket receiverSocket;
    public DatagramPacket receiveDataPacket;
    public DatagramPacket sendDataPacket;
    public String filePath;
    public int serverNumber = 0;
    public int receiveConnectState = 0;
    public int receiveDataPackageLen = 14;
    public int dataPackageLen = 0;
    public int receiveIndex = 0;
    public int segNumber = 0;
    public int dataSegmentNumber = 0;
    public int currSegmentNumber = 0;
    public int dupSegmentNumber = 0;
    public int dupAckNumber = 0;
    public int clientPort;
    private long startTime=System.currentTimeMillis();
    private InetAddress IPAddress;
    private receiverLog receiverLog;
    public static final String SND = "SND";
    public static final String RCV = "RCV";
    public static final String SND_DA = "SND/DA";
    public static final String SYN = "S";
    public static final String ACK = "A";
    public static final String FIN = "F";
    public static final String DATA = "D";
    public static final String SYN_ACK = "SA";
    public final static Integer HEAD_LENGTH = 14;

    /**
     * receiver constructor
     *
     * @param datagramSocket
     * @param fileName
     */
    public Receiver(DatagramSocket datagramSocket, String fileName) throws IOException {
        receiverSocket = datagramSocket;
        filePath = fileName;
        receiverLog = new receiverLog("Receiver_log.txt");
    }

    /**
     * receive data form Sender
     */
    public void receiveData() throws IOException {

        ArrayList<Integer> IndexArray = new ArrayList<>();
        ArrayList<receiverSegment> segmentArrayList = new ArrayList<>();
        Map<Integer, receiverSegment> map = new HashMap<>();


        try {
            while (receiveConnectState != 5) {
                receiverSegment receiveSegment = getReceiverSegment();
                if (receiveSegment != null) {
                    int senderSegSeq = receiveSegment.getSynNumber();
                    if (receiveConnectState == 0 && receiveSegment.getSYN() == 1) {
                        connect1(receiveSegment, senderSegSeq);
                    } else if (receiveConnectState == 1 && senderSegSeq == receiveIndex + 1 && receiveSegment.getAckNumber() == serverNumber && receiveSegment.getACK() == 1) {
                        connect2(receiveSegment, senderSegSeq);
                    } else if (receiveConnectState == 2 && receiveSegment.getFIN() == 1) {
                        connect3(receiveSegment, senderSegSeq);
                    } else if (receiveConnectState == 4 && senderSegSeq == receiveIndex + 1 && receiveSegment.getAckNumber() == serverNumber + 1 && receiveSegment.getACK() == 1) {
                        connect4(receiveSegment, senderSegSeq);
                    } else if (receiveConnectState == 2) {
                        if (connect5(IndexArray, segmentArrayList, map, receiveSegment, senderSegSeq)) continue;
                    }
                }
            }
            receiveRecordLog();

        } catch (SocketTimeoutException e) {
            receiveConnectState = 5;
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            receiverSocket.close();
            System.out.println("close");
        }
        this.writeSegmentToFile(filePath, segmentArrayList);
    }

    private receiverSegment getReceiverSegment() throws IOException {
        segNumber++;
        byte[] receiveData = new byte[receiveDataPackageLen];
        receiveDataPacket = new DatagramPacket(receiveData, receiveData.length);
        receiverSocket.receive(receiveDataPacket);
        IPAddress = receiveDataPacket.getAddress();
        clientPort = receiveDataPacket.getPort();
        byte[] dataArr = receiveDataPacket.getData();
        return getSegment(dataArr);
    }

    private void receiveRecordLog() throws IOException {
        receiverLog.receiveInfo("=============================================================");
        receiverLog.receiveInfo("Amount of data received (bytes)", receiveIndex - 1);
        receiverLog.receiveInfo("Total Segments Received", segNumber);
        receiverLog.receiveInfo("Data segments received", dataSegmentNumber);
        receiverLog.receiveInfo("Data segments with Bit Errors", currSegmentNumber);
        receiverLog.receiveInfo("Duplicate data segments received", dupSegmentNumber);
        receiverLog.receiveInfo("Duplicate ACKs sent", dupAckNumber);
        receiverLog.receiveInfo("=============================================================");
    }


    private void connect1(receiverSegment receiveSegment, int seq) throws IOException {
        receiveIndex = seq;
        startTime=System.currentTimeMillis();
        receiverLog.receiveInfo(RCV, startTime, this.SYN, seq, receiveSegment.getLen(), receiveSegment.getAckNumber());
        receiverSegment segment = new receiverSegment(serverNumber, receiveIndex + 1, 0, 1, 0, null);
        sendSegment(receiverSocket, segment, SND, SYN_ACK);
        serverNumber++;
        receiveConnectState = 1;
    }

    private void connect2(receiverSegment receiveSegment, int senderSegSeq) throws IOException {
        System.out.println("Connect successfully.");
        receiverLog.receiveInfo(RCV, startTime, ACK, senderSegSeq, receiveSegment.getLen(), receiveSegment.getAckNumber());
        receiveDataPackageLen = 1024;
        receiveIndex = senderSegSeq;
        receiveConnectState = 2;
    }

    private void connect3(receiverSegment receiveSegment, int senderSegSeq) throws IOException {
        System.out.println("begin close connection.");
        receiveIndex = senderSegSeq;
        receiverLog.receiveInfo(RCV, startTime, FIN, senderSegSeq, receiveSegment.getLen(), receiveSegment.getAckNumber());
        receiverSegment second = new receiverSegment(serverNumber, receiveIndex + 1, 1, 0, 0, null);
        sendSegment(receiverSocket, second, SND, ACK);
        receiveConnectState = 3;
        receiverSegment third = new receiverSegment(serverNumber, receiveIndex + 1, 1, 0, 1, null);
        sendSegment(receiverSocket, third, SND, FIN);
        receiveConnectState = 4;
    }

    private void connect4(receiverSegment receiveSegment, int senderSegSeq) throws IOException {
        receiverLog.receiveInfo(RCV, startTime, ACK, senderSegSeq, receiveSegment.getLen(), receiveSegment.getAckNumber());
        receiveConnectState = 5;
        System.out.println("END connection successfully.");
    }

    private boolean connect5(ArrayList<Integer> indexArray, ArrayList<receiverSegment> segmentArrayList, Map<Integer, receiverSegment> map, receiverSegment receiveSegment, int senderSegSeq) throws IOException {
        dataSegmentNumber++;
        if (dataPackageLen < receiveSegment.getLen()) {
            dataPackageLen = receiveSegment.getLen();
            receiveDataPackageLen = dataPackageLen + HEAD_LENGTH;
        }
        receiverLog.receiveInfo(RCV, startTime, DATA, senderSegSeq, receiveSegment.getLen(), receiveSegment.getAckNumber());
        String eventType = SND;
        if (senderSegSeq < receiveIndex) {
            dupSegmentNumber++;
            return true;
        } else if (senderSegSeq == receiveIndex) {
            segmentArrayList.add(receiveSegment);
            receiveIndex += receiveSegment.getLen();
            while (!indexArray.isEmpty() && receiveIndex == indexArray.get(0)) {
                int index = indexArray.get(0);
                indexArray.remove(0);
                receiverSegment indexSegment = map.get(index);
                segmentArrayList.add(indexSegment);
                map.remove(index);
                receiveIndex += indexSegment.getLen();
            }
        } else if (indexArray.isEmpty() || (indexArray.get(0) != senderSegSeq && !map.containsKey(senderSegSeq))) {
            indexArray.add(senderSegSeq);
            Collections.sort(indexArray);
            map.put(senderSegSeq, receiveSegment);
            eventType = SND_DA;
            dupAckNumber++;
        } else if (indexArray.get(0) == senderSegSeq || map.containsKey(senderSegSeq)) {
            dupSegmentNumber++;
            dupAckNumber++;
            eventType = SND_DA;
        }
        receiverSegment ack = new receiverSegment(serverNumber, receiveIndex, 1, 0, 0, null);
        sendSegment(receiverSocket, ack, eventType, ACK);
        return false;
    }


    public static receiverSegment getSegment(byte[] bytes) {
        receiverSegment segment = new receiverSegment();
        byte[] seqArr = getByteArr(bytes, 0, 4);
        byte[] ackArr = getByteArr(bytes, 4, 8);
        byte signal = getByte(bytes, 8);
        byte check = getByte(bytes, 9);
        byte[] len = getByteArr(bytes, 10, 14);
        int packageLen = Receiver.byteTransToInt(len);
        byte[] data = getByteArr(bytes, Receiver.HEAD_LENGTH, Receiver.HEAD_LENGTH + packageLen);
        segment.setData(data);
        segment.setSynNumber(Receiver.byteTransToInt(seqArr));
        segment.setAckNumber(Receiver.byteTransToInt(ackArr));
        segment.setACK(signal / 4);
        signal %= 4;
        segment.setSYN(signal / 2);
        signal %= 2;
        segment.setCheckNum(check);
        segment.setFIN(signal);
        segment.setLen(packageLen);
        return segment;
    }


    public static byte[] getByteArr(byte[] bytes, int from, int to) {
        byte[] b = new byte[to - from];
        for (int i = from; i < to; i++) {
            b[i - from] = bytes[i];
        }
        return b;
    }


    private void sendSegment(DatagramSocket senderSocket, receiverSegment segment, String event, String packet) throws IOException {
        sendDataPacket = new DatagramPacket(segment.getBytes(), segment.getBytes().length, IPAddress, clientPort);
        senderSocket.send(sendDataPacket);
        receiverLog.receiveInfo(event, startTime, packet, segment.getSynNumber(), segment.getLen(), segment.getAckNumber());
    }


    public static void writeSegmentToFile(String path, ArrayList<receiverSegment> segmentList) {
        File file = new File(path);
        RandomAccessFile accessFile = null;
        try {
            accessFile = new RandomAccessFile(file, "rw");
            int start = 0;
            for (receiverSegment segment : segmentList) {
                byte[] data = segment.getData();
                int len = segment.getLen();
                byte[] real = getByteArr(data, 0, len);
                accessFile.seek(start);
                accessFile.write(real, 0, len);
                start += len;
            }
            accessFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static byte[] IntTransToByte(int value) {
        byte[] src = new byte[4];
        src[0] = (byte) ((value >> 24) & 0xFF);
        src[1] = (byte) ((value >> 16) & 0xFF);
        src[2] = (byte) ((value >> 8) & 0xFF);
        src[3] = (byte) (value & 0xFF);
        return src;
    }


    public static int byteTransToInt(byte[] byteArr) {
        int value;
        value = (int) (((byteArr[0] & 0xFF) << 24)
                | ((byteArr[0 + 1] & 0xFF) << 16)
                | ((byteArr[0 + 2] & 0xFF) << 8)
                | (byteArr[0 + 3] & 0xFF));
        return value;
    }

    /**
     * merge byte array
     *
     * @param arrays
     * @return
     */
    public static byte[] getOneArray(byte[]... arrays) {
        int sumLength = 0;
        for (int i = 0; i < arrays.length; i++) {
            sumLength += arrays[i].length;
        }
        byte[] newByteArr = new byte[sumLength];
        int index = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, newByteArr, index, array.length);
            index += array.length;
        }
        return newByteArr;
    }

    public static void main(String[] args) {

        //args = new String[]{"9999", "666.pdf"};
        if (args.length == 2) {
            try {
                System.out.println("Receiver start");
                Receiver receiver = new Receiver(new DatagramSocket( Integer.parseInt(args[0])), args[1]);
                receiver.receiveData();
            } catch (NumberFormatException e) {
                System.out.println("wrong receiver port.");
                e.printStackTrace();
            } catch (SocketException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
