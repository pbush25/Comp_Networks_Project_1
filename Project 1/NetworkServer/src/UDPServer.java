/**
 * Created by King on 3/2/2017.
 */
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;

class UDPServer {
    public static DatagramSocket serverSocket;
    public static final int PACKET_LENGTH = 128;

    public static void main(String args[]) throws Exception {
        try {
            serverSocket = new DatagramSocket(10040);
        } catch (SocketException e) {
            System.out.println("Unable to open socket... " + e.getLocalizedMessage());
            throw e;
        }

        UDPServerHelper server = new UDPServerHelper();
        server.receiveRequest();


//        while (true) {
//            //Receive datagram
//
//
//            String sentence = new String(receivePacket.getData());
//
//            InetAddress IPAddress = receivePacket.getAddress();
//
//            int port = receivePacket.getPort();
//
//            String capitalizedSentence = sentence.toUpperCase();
//
//            sendData = capitalizedSentence.getBytes();
//
//
//        }
    }
}

class UDPServerHelper {
    private byte[] receiveData = new byte[1024];
    private byte[] sendData = new byte[UDPServer.PACKET_LENGTH];
    private byte[] fileData;

    private int incomingPort;
    private InetAddress incomingAddress;

    public void receiveRequest() throws IOException {
        while (true) {
            System.out.println("Listening for request...");
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            try {
                UDPServer.serverSocket.receive(receivePacket);
            } catch (IOException e) {
                System.out.println("Unable to receive packet... " + e.getLocalizedMessage());
                throw e;
            }

            String request = new String(receivePacket.getData());
            System.out.println("Received packet with request -- " + request);
            if (processRequest(request)) {
                System.out.println("Did process request");
                incomingPort = receivePacket.getPort();
                incomingAddress = receivePacket.getAddress();
                processResponse();
            }

        }
    }

    public void sendPacket(int port, InetAddress ipAddress) throws IOException {
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ipAddress, port);
        try {
            UDPServer.serverSocket.send(sendPacket);
        } catch (IOException e) {
            System.out.println("Unable to send packet... " + e.getLocalizedMessage());
            throw e;
        }
    }

    private boolean processRequest(String request) {
        System.out.println("Processing request...");
        // Parse the request and make sure it's in the right format
        String[] requestComponents = (request.trim().split("\\s+"));
        if (requestComponents.length != 3) {
            System.out.println("Couldn't separate request into valid components");
            // it was a bad request
            return false;
        }
        System.out.println("Received valid request... processing");

        // The second token should be the filename, so let's get it
        fileData = new byte[0];
        String fileName = requestComponents[1];
        readFile(fileName);
        if (fileData.length == 0) {
            // we read the file but it was empty
            return false;
        }
        return true;
    }

    private void processResponse() {
        // break the file up into packets and send them
        int fileByteCounter = 0;
        byte[] packet = new byte[UDPServer.PACKET_LENGTH];

        // Create the packet header
        String packetHeader = "HTTP/1.0 200 Document Follows\r\n";
        packetHeader += "Content-Type: text/plain\r\n";
        packetHeader += "Content-Length: " + fileData.length + "\r\n";
        packetHeader += "\r\n";

        int headerLength = packetHeader.getBytes().length;

        String packetInfo;

        while(fileByteCounter < fileData.length) {
            byte[] tempFileBytes;
            int packetSize = fileData.length - fileByteCounter;
            int dataLength = UDPServer.PACKET_LENGTH - headerLength;
            if (packetSize > dataLength) {
                tempFileBytes = Arrays.copyOfRange(fileData, fileByteCounter, fileByteCounter += dataLength);
            } else {
                tempFileBytes = Arrays.copyOfRange(fileData, fileByteCounter, fileByteCounter += packetSize);
            }

            packetInfo = packetHeader + new String(tempFileBytes);

            packet = packetInfo.getBytes();
            System.out.println("Sending packet with data " + new String(packet));
            sendData = new byte[UDPServer.PACKET_LENGTH];
            sendData = packet;

            // send the packet
            try {
                sendPacket(incomingPort, incomingAddress);
            } catch (IOException e) {
                System.out.println("Unable to process response " + e.getLocalizedMessage());
                return;
            }
        }
    }

    private void readFile(String fileName) {
        try {
            File file = new File("./" + fileName);
            if (!file.isFile()) {
                return;
            }

            fileData = Files.readAllBytes(file.toPath());
            System.out.println("Found and read data from file. Processing file...");
            // have to add 1 byte with null to indicate termination
            byte[] tempFileData = new byte[fileData.length + 1];
            for (int i = 0; i < fileData.length; i++) {
                // copy the og data
                tempFileData[i] = fileData[i];
            }
            // set the last byte to null && assign back to buffer
            tempFileData[tempFileData.length - 1] = 0;
            fileData = tempFileData;
        } catch (IOException e) {
            System.out.println("Couldn't read requested file " + e.getMessage());
        }
    }
}
