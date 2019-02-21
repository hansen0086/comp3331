
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Sender extends Thread {
    public DatagramSocket clientSocket;
    public DatagramPacket receivePacket;
    public DatagramPacket sendPacket;

    private String receiver_host_ip;
    private int receiver_port;
    private int MWS;
    private int MSS;
    private int gamma;
    private float pDrop;
    private float pDuplicate;
    private float pCorrupt;
    private float pOrder;
    private int maxOrder;
    private float pDelay;
    private int maxDelay;
    private int seed;


    private InetAddress IPAddress;
    private long startTime;
    private senderLog senderRecordLog;
    private PLDModule pModule;
    //String const
    public static final int EstimatedRTT = 500;
    public static final int DevRTT = 250;
    public static final String SND = "SND";
    public static final String RCV = "RCV";
    public static final String DROP = "DROP";
    public static final String CORR = "CORR";
    public static final String DUP = "DUP";
    public static final String RORD = "RORD";
    public static final String DELY = "DELY";
    public static final String RCV_DA = "RCV/DA";
    public static final String SND_RXT = "SND/RXT";

    public static final String SYN = "S";
    public static final String ACK = "A";
    public static final String FIN = "F";
    public static final String DATA = "D";
    public static final String SYN_ACK = "SA";
    public final static Integer HEAD_LENGTH = 14;
    public final static float EXP = 1e-3f;
    private static String file;

    private volatile int state = 0;
    private volatile int indexSize = 0;
    private volatile int nextSegmentIndex = 0;
    private volatile int transmitNumber = 0;
    private volatile int timeoutNumber = 0;
    private volatile int fastNumber = 0;
    private volatile int dupACKNumber = 0;


    public Sender(DatagramSocket soc, argsParse parse) throws IOException {
        this.clientSocket = soc;
        this.receiver_host_ip = parse.getReceiver_host_ip();
        this.receiver_port = parse.getReceiver_port();
        this.MSS = parse.getMSS();
        this.MWS = parse.getMWS();
        this.gamma = parse.getGamma();
        this.pDrop = parse.getpDrop();
        this.pDuplicate = parse.getpDuplicate();
        this.pCorrupt = parse.getpCorrupt();
        this.pOrder = parse.getpOrder();
        this.maxOrder = parse.getMaxOrder();
        this.pDelay = parse.getpDelay();
        this.maxDelay = parse.getMaxDelay();
        this.seed = parse.getSeed();
        this.file = parse.getFile();
        this.startTime = System.currentTimeMillis();
        this.senderRecordLog = new senderLog("Sender_log" + ".txt");
        IPAddress = InetAddress.getByName(receiver_host_ip);
        pModule = new PLDModule(soc, senderRecordLog, startTime, receiver_host_ip, receiver_port, seed);
    }


    private void sendData() throws UnknownHostException {
        int clientNum = 0;
        int serverNum = 0;
        int PLDAmount = 0;
        long timeout = EstimatedRTT + this.gamma * DevRTT;
        try {
            serverNum = threeHandShake(clientNum, serverNum);
            clientNum++;
            indexSize = clientNum;
            nextSegmentIndex = clientNum;
            List<sendSegment> segmentList = readFile(this.MSS, clientNum, serverNum);
            int fileSize = 0;
            for (sendSegment segment : segmentList) {
                fileSize += segment.getLen();
            }
            int finalFileSize = fileSize;
            Map<Integer, Long> timeList = new ConcurrentHashMap<>();
            Thread receiveThread = getThread(segmentList, finalFileSize);
            receiveThread.start();
            Timer timer = getTimer(timeout, segmentList, timeList);
            PLDAmount = sendFileToReceiver(PLDAmount, segmentList, fileSize, timeList);
            pModule.close();
            fourHandShake(serverNum);
            timer.cancel();
            senderRecordLog(PLDAmount, fileSize);

        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    private Thread getThread(List<sendSegment> segmentList, int finalFileSize) {
        return new Thread() {
            public void run() {
                try {
                    Map<Integer, Integer> ackCountMap = new HashMap<>();
                    while (state == 2 && indexSize < finalFileSize) {
                        sendSegment reSegment = receiveSegment();
                        int reAckNumber = reSegment.getAckNumber();
                        int reSeqNumber = reSegment.getSynNumber();
                        if (indexSize < reAckNumber && reSegment.getACK() == 1) {
                            senderRecordLog.sendInfo(RCV, startTime, ACK, reSeqNumber, reSegment.getLen(), reAckNumber);
                            indexSize = reAckNumber;
                        } else {
                            dupACKNumber++;
                            senderRecordLog.sendInfo(RCV_DA, startTime, ACK, reSeqNumber, reSegment.getLen(), reAckNumber);
                            if (ackCountMap.containsKey(reAckNumber)) {
                                int count = ackCountMap.get(reAckNumber) + 1;
                                if (count >= 3) {
                                    sendSegment fileSegment = segmentList.get(reAckNumber / MSS);
                                    sendSegment(clientSocket, fileSegment, SND_RXT, DATA);
                                    fastNumber++;
                                    transmitNumber++;
                                    ackCountMap.remove(reAckNumber);
                                } else {
                                    ackCountMap.put(reAckNumber, count);
                                }
                            } else {
                                ackCountMap.put(reAckNumber, 1);
                            }
                        }
                    }
                    state = 3;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
    }

    private void senderRecordLog(int PLDAmount, int fileSize) throws IOException {
        senderRecordLog.sendInfo("=============================================================");
        senderRecordLog.sendInfo("Size of the file (Bytes)", fileSize);
        senderRecordLog.sendInfo("Segments transmitted (including drop & RXT)", transmitNumber);
        senderRecordLog.sendInfo("Number of Segments handled by PLDModule", PLDAmount);
        senderRecordLog.sendInfo("Number of Segments dropped", pModule.getDropAmount());
        senderRecordLog.sendInfo("Number of Segments Corrupted", pModule.getCorruptedAmount());
        senderRecordLog.sendInfo("Number of Segments Re-ordered", pModule.getReorderAmount());
        senderRecordLog.sendInfo("Number of Segments Duplicated", pModule.getDuplicatedAmout());
        senderRecordLog.sendInfo("Number of Segments Delayed", pModule.getDelayAmount());
        senderRecordLog.sendInfo("Number of Retransmissions due to TIMEOUT", timeoutNumber);
        senderRecordLog.sendInfo("Number of FAST RETRANSMISSION", fastNumber);
        senderRecordLog.sendInfo("Number of DUP ACKS received", dupACKNumber);
        senderRecordLog.sendInfo("=============================================================");
    }

    private int sendFileToReceiver(int PLDAmount, List<sendSegment> segmentList, int fileSize, Map<Integer, Long> map) throws IOException {
        while (indexSize < fileSize) {
            while (nextSegmentIndex < fileSize && nextSegmentIndex - indexSize <= MWS) {
                sendSegment fileSegment = segmentList.get(nextSegmentIndex / MSS);
                pModule.dealSegment(fileSegment, this.receiver_host_ip, this.receiver_port, this.seed, this.pDrop, this.pDuplicate, this.pCorrupt, this.pOrder, this.pDelay, this.maxOrder, this.maxDelay, SND, DATA);
                map.put(fileSegment.getSynNumber(), System.currentTimeMillis());
                nextSegmentIndex += fileSegment.getLen();
                transmitNumber++;
                PLDAmount++;
            }
        }
        return PLDAmount;
    }

    private void fourHandShake(int serverNum) throws IOException {
        boolean closed = false;
        boolean waitClosed = false;
        sendSegment segment = new sendSegment(nextSegmentIndex, serverNum, 0, 0, 1, null);
        sendSegment(clientSocket, segment, SND, FIN);
        while (!closed) {
            sendSegment receiveSegment = receiveSegment();
            int ack_number = receiveSegment.getAckNumber();
            int ACKNumber = receiveSegment.getACK();
            int FINNumber = receiveSegment.getFIN();
            int receive_seq = receiveSegment.getSynNumber();
            if (!waitClosed && ACKNumber == 1 && ack_number == nextSegmentIndex + 1) {
                System.out.println("Wait to closed.");
                senderRecordLog.sendInfo(RCV, startTime, ACK, receiveSegment.getSynNumber(), receiveSegment.getLen(), receiveSegment.getAckNumber());
                waitClosed = true;
            } else if (waitClosed && FINNumber == 1 && ACKNumber == 1 && ack_number == nextSegmentIndex + 1) {
                senderRecordLog.sendInfo(RCV, startTime, FIN, receiveSegment.getSynNumber(), receiveSegment.getLen(), receiveSegment.getAckNumber());
                sendSegment fourth = new sendSegment(nextSegmentIndex + 1, receive_seq + 1, 1, 0, 0, null);
                sendSegment(clientSocket, fourth, this.SND, this.ACK);
                System.out.println("Close Sender connection successfully.");
                closed = true;
            }
        }
    }

    private Timer getTimer(long timeout, List<sendSegment> segmentList, Map<Integer, Long> map) {
        //start timer
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    for (Integer i : map.keySet()) {
                        long time = System.currentTimeMillis() - map.get(i);
                        if (i >= indexSize && time > timeout) {
                            sendSegment fileSegment = segmentList.get(i / MSS);
                            sendSegment(clientSocket, fileSegment, SND_RXT, DATA);
                            transmitNumber++;
                            timeoutNumber++;
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, 1000, 500);
        return timer;
    }

    private int threeHandShake(int clientNum, int serverNum) throws IOException {
        while (state != 2) {
            sendSegment syn = new sendSegment(clientNum, serverNum, 0, 1, 0, null);
            sendSegment(clientSocket, syn, SND, SYN);
            state = 1;
            sendSegment saSegment = receiveSegment();
            senderRecordLog.sendInfo(RCV, startTime, SYN_ACK, saSegment.getSynNumber(), saSegment.getLen(), saSegment.getAckNumber());
            if (saSegment.getSYN() == 1 && saSegment.getAckNumber() == clientNum + 1) {
                serverNum = saSegment.getSynNumber() + 1;
                sendSegment third = new sendSegment(clientNum + 1, serverNum, 1, 0, 0, null);
                sendSegment(clientSocket, third, SND, ACK);
                state = 2;
            }
        }
        return serverNum;
    }


    /**
     * send segment
     *
     * @param senderSocket
     * @param segment
     * @param eventType
     * @param packetType
     * @throws IOException
     */
    public void sendSegment(DatagramSocket senderSocket, sendSegment segment, String eventType, String packetType) throws IOException {
        sendPacket = new DatagramPacket(segment.getBytes(), segment.getBytes().length, IPAddress, receiver_port);
        senderSocket.send(sendPacket);
        senderRecordLog.sendInfo(eventType, startTime, packetType, segment.getSynNumber(), segment.getLen(), segment.getAckNumber());
    }

    /**
     * receive segment add by zj
     *
     * @return
     * @throws IOException
     */
    private sendSegment receiveSegment() throws IOException {
        byte[] receiveSegmentArr = new byte[HEAD_LENGTH];
        receivePacket = new DatagramPacket(receiveSegmentArr, receiveSegmentArr.length);
        clientSocket.receive(receivePacket);
        return getSegment(receiveSegmentArr);
    }

    public static sendSegment getSegment(byte[] bytes) {
        sendSegment segment = new sendSegment();
        byte[] seqArr = getByteArr(bytes, 0, 4);
        byte[] ackArr = getByteArr(bytes, 4, 8);
        byte signal = getByte(bytes, 8);
        byte check = getByte(bytes, 9);
        byte[] len = getByteArr(bytes, 10, 14);
        int packageLen = Sender.byteTransToInt(len);
        byte[] data = getByteArr(bytes, Sender.HEAD_LENGTH, Sender.HEAD_LENGTH + packageLen);
        segment.setData(data);
        segment.setSynNumber(Sender.byteTransToInt(seqArr));
        segment.setAckNumber(Sender.byteTransToInt(ackArr));
        segment.setACK(signal / 4);
        signal %= 4;
        segment.setSYN(signal / 2);
        signal %= 2;
        segment.setCheckNum(check);
        segment.setFIN(signal);
        segment.setLen(packageLen);
        return segment;
    }

    public static byte getByte(byte[] bytes, int index) {
        return bytes[index];
    }

    public static byte[] getByteArr(byte[] bytes, int from, int to) {
        byte[] b = new byte[to - from];
        for (int i = from; i < to; i++) {
            b[i - from] = bytes[i];
        }
        return b;
    }

    public static ArrayList<sendSegment> readFile(int MSS, int seq, int ack) throws IOException {
        ArrayList<sendSegment> segmentList = new ArrayList<>();
        RandomAccessFile accessFile = new RandomAccessFile(Sender.file, "r");
        byte[] byteArr = new byte[MSS];
        int index = 0;
        while ((index = accessFile.read(byteArr)) != -1) {
            byte[] b = Arrays.copyOf(byteArr, index);
            sendSegment segment = new sendSegment(seq, ack, b);
            segmentList.add(segment);
            seq += index;
        }
        accessFile.close();
        return segmentList;
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


    public static void main(String[] args) throws IOException {
        //args = new String[]{"127.0.0.1", "9999", "test2.pdf", "500", "100", "4", "0.1", "0", "0", "0", "4", "0", "0", "100"};
        if (args.length != 14) {
            System.out.println("Error args!!!");
        } else {
            argsParse parse = new argsParse(args);
            DatagramSocket socket = null;
            try {
                socket = new DatagramSocket();
                Sender sender = new Sender(socket, parse);
                System.out.println("Sender begin to run.");
                sender.sendData();
            } finally {
                socket.close();
            }
        }
    }


}
