/**
 * Created by King on 3/2/2017.
 */
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.Arrays;

/**
 * UDPServer class
 * Usage java UDPServer
 * Accepts an incoming GET message requesting a file and serves up the file
 */
class UDPServer {
    static DatagramSocket serverSocket;
    static final int PACKET_LENGTH = 128;
    static final int HEADER_LENGTH = 40;

    /**
     * Main runloop for server; always running
     * @param args none
     * @throws Exception if an exception is thrown
     */
    public static void main(String args[]) throws Exception {
        try {
            serverSocket = new DatagramSocket(10040);
        } catch (SocketException e) {
            System.out.println("Unable to open socket... " + e.getLocalizedMessage());
            throw e;
        }

        //create the server object and begin receiving incoming requests
        UDPServerHelper server = new UDPServerHelper();
        server.receiveRequest();
    }
}

/**
 * Helper class is actual server
 */
class UDPServerHelper {
    private byte[] receiveData = new byte[1024];
    private byte[] sendData = new byte[UDPServer.PACKET_LENGTH];
    private byte[] fileData;

    private int incomingPort;
    private InetAddress incomingAddress;

    /**
     * Kicks off request receipt loop...will always be listening
     * @throws IOException if an exception is thrown
     */
    void receiveRequest() throws IOException {
        // always listen
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

            incomingPort = receivePacket.getPort();
            incomingAddress = receivePacket.getAddress();
            if (processRequest(request)) {
                // if we can process the request
                System.out.println("Did process request");
                executeTransaction();
            }
        }
    }

    /**
     * Executes the transaction with the client for what they requested
     */
    private void executeTransaction() {
        sendHTTPResponse(buildNormalResponse());
        processAndSendData();
        sendTerminatingPacket();
    }

    /**
     * Sends a UDP packet out over the datagram
     * Information should be in sendData
     * @param port int the outgoing port
     * @param ipAddress InetAddress the IP address to send to
     * @throws IOException
     */
    private void sendPacket(int port, InetAddress ipAddress) throws IOException {
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ipAddress, port);
        try {
            UDPServer.serverSocket.send(sendPacket);
        } catch (IOException e) {
            System.out.println("Unable to send packet... " + e.getLocalizedMessage());
            throw e;
        }
    }

    /**
     * Process the request sent by the client
     * @param request the HTTP request sent from the client
     * @return true if can process request, false otherwise
     */
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
        if (!readFile(fileName)) {
            String fnfResponse = buildFileNotResponse();
            sendHTTPResponse(fnfResponse);
            return false;
        }

        return fileData.length != 0;
    }

    /**
     * Send the appropriate HTTP response to the client
     * @param response String the response to send
     */
    private void sendHTTPResponse(String response) {

        sendData = new byte[UDPServer.PACKET_LENGTH];
        sendData = response.getBytes();
        System.out.println("Responding to HTTP request with response: \n" + response);
        try {
            sendPacket(incomingPort, incomingAddress);
        } catch (IOException e) {
            System.out.println("Unable to send HTTP response with error: " + e.getLocalizedMessage());
        }
    }

    /**
     * Build the normal HTTP response
     * @return normal HTTP response
     */
    private String buildNormalResponse() {
        String response = "HTTP/1.0 200 Document Follows\r\n";
        response += "Content-Type: text/plain\r\n";
        response += "Content-Length: " + fileData.length + "\r\n";
        response += "\r\n";

        return response;
    }

    /**
     * Build File not Found HTTP response
     * @return 404 File Not Found
     */
    private String buildFileNotResponse() {
        String response = "HTTP/1.0 404 File not found\r\n";
        response += "Content-Type: text/plain\r\n";
        response += "Content-Length: " + fileData.length + "\r\n";
        response += "\r\n";

        return response;
    }

    /**
     * Process the requested file and send the data
     */
    private void processAndSendData() {
        // break the file up into packets and send them
        int fileByteCounter = 0;
        int sequenceNumber = 0;
        byte[] packet;

        // Create the packet header
        String packetHeader;
        String packetInfo;

        while(fileByteCounter < fileData.length) {
            byte[] tempFileBytes;
            int packetSize = fileData.length - fileByteCounter;
            int dataLength = UDPServer.PACKET_LENGTH - UDPServer.HEADER_LENGTH;

            // get the rest of the file data for the packet
            if (packetSize > dataLength) {
                tempFileBytes = Arrays.copyOfRange(fileData, fileByteCounter, fileByteCounter += dataLength);
            } else {
                tempFileBytes = Arrays.copyOfRange(fileData, fileByteCounter, fileByteCounter += packetSize);
            }

            // compute the checksum and create packet header
            int checksum = computeChecksum(tempFileBytes);
            packetHeader = createDataPacketHeader(sequenceNumber, checksum);

            packetInfo = packetHeader + new String(tempFileBytes);

            // covert the packet info to the packet
            packet = packetInfo.getBytes();

            System.out.println("Sending packet with data \n" + new String(packet));
            sendData = new byte[UDPServer.PACKET_LENGTH];
            sendData = packet;

            // send the packet
            try {
                sendPacket(incomingPort, incomingAddress);
                sequenceNumber++;
            } catch (IOException e) {
                System.out.println("Unable to process response " + e.getLocalizedMessage());
                return;
            }
        }
    }

    /**
     * Tries to read the requested filename
     * @param fileName the requested filename
     * @return return true iff can read file
     */
    private boolean readFile(String fileName) {
        try {
            // Try to find the file
            File file = new File("./" + fileName);
            if (!file.isFile()) {
                return false;
            }

            // Found, now read file
            fileData = Files.readAllBytes(file.toPath());
            System.out.println("Found and read data from file. Processing file...");
        } catch (IOException e) {
            System.out.println("Couldn't read requested file " + e.getMessage());
        }
        return true;
    }

    /**
     * Create the header for a data packet
     * @param sequenceNumber the sequence number of the packet > 0
     * @param checksum the checksum of the data > 0
     * @return a proper header for the packet w/ info
     */
    private String createDataPacketHeader(int sequenceNumber, int checksum) {
        String packetHeader;
        packetHeader = ("Sequence #" + sequenceNumber + "\r\n");
        packetHeader += ("Checksum #" + checksum + "\r\n");
        packetHeader += "&\r\n";

        return packetHeader;
    }

    /**
     * Computes the checksum of the packet for error detection
     * @param packet the packet for which to calculate the checksum
     * @return the calculated checksum value
     */
    private int computeChecksum(byte[] packet) {
        int checksum = 0;
        for (Byte b: packet) {
            checksum += b.intValue();
        }
        return checksum;
    }

    /**
     * Send a terminating packet.
     * Send after finish file transfer so client knows ft is complete.
     */
    private void sendTerminatingPacket() {
        System.out.println("Sending terminating packet...");
        sendData = new byte[1];
        sendData[0] = 0;
        try {
            sendPacket(incomingPort, incomingAddress);
        } catch (IOException e) {
            System.out.println("Unable to send terminating packet... " + e.getLocalizedMessage());
        }
    }
}
