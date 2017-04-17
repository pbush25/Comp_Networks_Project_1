/**
 * Created by King on 3/2/2017.
 */

import com.sun.xml.internal.bind.v2.runtime.reflect.Lister;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Random;

import static java.lang.System.exit;

/**
 * Main UDPClient
 */
class UDPClient {
    static final int PACKET_LENGTH = 512;
    static DatagramSocket clientSocket;
    static double damagedPacketProbability;
    static double lostPacketProbability;
    static double delayedPacketProbability;
    static double delayedPacketTime;
    static String fileName;
    static UDPClientHelper client;
    static String inetAddress;
    static BufferedReader inputBuffer;

    /**
     * Main UDPClient method
     *
     * @param args can include the corruptibility percentage
     * @throws Exception if unable to handle some exception
     */
    public static void main(String args[]) throws Exception {
        // check if a damaged packet probability was passed in
        if (args.length > 0) {
            // set the probability
            try {
                damagedPacketProbability = Double.parseDouble(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Error! Damaged packet probability must be an double!");
                System.out.println("Usage: java UDPClient <double>");
                exit(0);
            }
        }

        //Set Client variables
        while (!getUserInput()) { }

        // begin client runloop
        kickoffRunloop();
    }

    /**
     * Allows the user to set the probability of packet corruption, loss, and delay. Also sets the delay time in milliseconds
     * and the server inet address.
     */
    private static boolean getUserInput() {
       try {
           inputBuffer = new BufferedReader(new InputStreamReader(System.in));

           System.out.print("Enter the probability that a packet is damaged (0.2 = 20%): ");
           damagedPacketProbability = Double.parseDouble(inputBuffer.readLine().trim());

           System.out.print("Enter the probability that a packet is lost: ");
           lostPacketProbability = Double.parseDouble(inputBuffer.readLine().trim());

           System.out.print("Enter the probability that a packet is delayed: ");
           delayedPacketProbability = Double.parseDouble(inputBuffer.readLine().trim());

           System.out.print("Enter the delay time for packets (in milliseconds): ");
           delayedPacketTime = Double.parseDouble(inputBuffer.readLine().trim());

           System.out.print("Enter the server inet address: ");
           inetAddress = inputBuffer.readLine().trim();

           return true;

       }
       catch (Exception e) {
           System.out.println("Error reading in Gremlin variables. Error Message: " + e.getLocalizedMessage());
           return false;
       }
    }

    /**
     * Main runloop of the client
     */
    private static void kickoffRunloop() {
        // always be prepared to accept a HTTP request input
        while (true) {
            inputBuffer = new BufferedReader(new InputStreamReader(System.in));
            try {
                clientSocket = new DatagramSocket();
                client = new UDPClientHelper(inetAddress);
                System.out.print("Enter HTTP Request: ");
                String httpRequest = inputBuffer.readLine();
                String[] requestComponents = (httpRequest.trim().split("\\s+"));
                if (!handleBadRequest(requestComponents)) {
                    continue;
                }
                // otherwise perform transaction
                fileName = requestComponents[1];
                client.executeTransaction(httpRequest);
            } catch (IOException e) {
                System.out.println("Client error " + e.getLocalizedMessage());
            }
        }
    }

    private static boolean handleBadRequest(String[] requestComponents) {
        if (requestComponents.length != 3) {
            // bad request
            System.out.println("Bad request!\n Usage: java UDPClient GET TestFile.html HTTP/2.0");
            return false;
        }
        if (!requestComponents[1].contains(".")) {
            // bad file name
            System.out.println("Bad request!\n Couldn't parse file name.");
            return false;
        }
        return true;
    }
}

/**
 * A helper for the web client
 */
class UDPClientHelper {
    private byte[] sendData;
    private byte[] receiveData = new byte[UDPClient.PACKET_LENGTH];
    private InetAddress ipAddress;
    private int incomingPort;
    private byte[] file;
    private int fileSize;
    private String fileInfo = "";
    private int expectedSequenceNumber = 0;
    private PacketCondition currentPacketCondition;

    /**
     * Create a new UDPClientHelper class
     *
     * @param ipAddressAssigned String the assigned ip address for the request
     */
    UDPClientHelper(String ipAddressAssigned) {
        try {
            ipAddress = InetAddress.getByName(ipAddressAssigned);
        } catch (UnknownHostException e) {
            System.out.println("Unable to resolve host " + e.getLocalizedMessage());
            exit(1);
        }
    }

    void executeTransaction(String request) throws IOException {
        sendHTTPRequest(request);
        try {
            receiveHTTPResponse();
        } catch (SocketTimeoutException e) {
            System.out.println("Server not responding... \r\n\r\n");
            UDPClient.clientSocket.close();
            return;
        } catch (IOException e) {
            return;
        }
        loadFileFromResponse();
        listenForNullPacket();
        System.out.println("Finished processing input... \n\n\n");
        return;
    }

    /**
     * Send an HTTP request to the server
     *
     * @param request String the HTTP request
     * @throws IOException E if unable to send the packet
     */
    private void sendHTTPRequest(String request) throws IOException {
        sendData = request.getBytes();

        // Create the datagram packet
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ipAddress, 10040);
        try {
            UDPClient.clientSocket.setSoTimeout(2000);
            UDPClient.clientSocket.send(sendPacket);
        } catch (IOException e) {
            System.out.println("Unable to send HTTP request " + e.getLocalizedMessage());
        }
    }

    /**
     * Listen for and process an HTTP response from the server
     */
    private void receiveHTTPResponse() throws IOException {
        // Receive the HTTP request response
        try {
            receivePacket();
            byte[] httpResponse = receiveData;
            int start = new String(httpResponse).lastIndexOf(':');
            int end = new String(httpResponse).indexOf("\r", start);
            if (start == -1 || end == -1) {
                System.out.println("Unable to process HTTP response -- BAD HEADER");
                return;
            }
            fileSize = Integer.parseInt(new String(httpResponse).substring(start + 1, end).trim());
            int responseCode = Integer.parseInt(new String(httpResponse).substring(9, 12));
            ResponseCodes response = getResponseString(responseCode);
            if (response != ResponseCodes.OKAY) {
                // invalid response
                if (response == ResponseCodes.NOT_FOUND) {
                    throw new IOException("404 FILE NOT FOUND");
                } else {
                    throw new IOException(response.toString());
                }
            }

            System.out.println("Received HTTP response \n" + new String(trim(receiveData)));
        } catch (SocketTimeoutException to) {
            System.out.println("Timed out waiting for server... ");
            throw to;
        }
        catch (IOException | NumberFormatException | ArrayIndexOutOfBoundsException e) {
            System.out.println("Unable to process HTTP response " + e.getLocalizedMessage());
            throw e;
        }

    }

    /**
     * After receiving the response, load the file requested
     */
    private void loadFileFromResponse() {
        // get the file packets while the size of the data is less than the file size
        while (fileInfo.length() < fileSize) try {
            receivePacket();
            byte[] packet = trim(receiveData); // remove null bytes
            String header = new String(packet).substring(0, new String(packet).indexOf('&')); // just header
            int expectedChecksum = Integer.parseInt(header.substring(header.lastIndexOf('#') + 1, header.lastIndexOf('\r')));
            String sequenceNumber = header.substring(header.indexOf('#') + 1, header.indexOf('\r'));

            //Run the gremlin
            packet = gremlin(packet, header.length());


            String packetString = new String(packet).substring(new String(packet).indexOf('&') + 3); // find EOH

            if (currentPacketCondition == PacketCondition.LOST) {
                //Act like we never received a packet.
                continue;
            }
            else if (Integer.parseInt(sequenceNumber) != expectedSequenceNumber) {
                //Send ACK with expected sequence number and print error message
                System.out.println("Expected sequence number " + expectedSequenceNumber + " but received sequence number: " + sequenceNumber);
                sendACK();
            }
            else if (currentPacketCondition == PacketCondition.DELAYED) {
                //Async Delay
            }
            else if (!checksumErrorExists(expectedChecksum, packetString.getBytes())) {
                System.out.println("Checksum error exists! Bad sequence number: " + sequenceNumber);
                sendNACK();
            }
            else {
                expectedSequenceNumber++;
                sendACK();

                fileInfo += packetString;
                System.out.println("Received data in packet \n" + new String(packet));
                packet = null;
            }




        } catch (IOException | ArrayIndexOutOfBoundsException e) {
            System.out.println("Unable to load file from response " + e.getStackTrace());
            return;
        }
    }

    private void sendACK() {
        // Create the packet header
        byte[] packet;
        String packetHeader;
        String packetInfo;

        //Super simple message, might need to add more
        String ack = "ACK";

        // compute the checksum and create packet header
        int checksum = computeChecksum(ack.getBytes());

        packetHeader = createDataPacketHeader(expectedSequenceNumber, checksum);

        packetInfo = packetHeader + ack;

        // covert the packet info to the packet
        packet = packetInfo.getBytes();

        System.out.println("Sending ACK with sequence number: " + expectedSequenceNumber);
        sendData = new byte[UDPClient.PACKET_LENGTH];
        sendData = packet;

        // send the packet
        try {
            sendPacket(incomingPort, ipAddress);
        } catch (IOException e) {
            System.out.println("Unable to process response " + e.getLocalizedMessage());
        }
    }

    private void sendNACK() {
        // Create the packet header
        byte[] packet;
        String packetHeader;
        String packetInfo;

        //Super simple message, might need to add more
        String nack = "NACK";

        // compute the checksum and create packet header
        int checksum = computeChecksum(nack.getBytes());

        packetHeader = createDataPacketHeader(expectedSequenceNumber, checksum);

        packetInfo = packetHeader + nack;

        // covert the packet info to the packet
        packet = packetInfo.getBytes();

        System.out.println("Sending NACK with sequence number: " + expectedSequenceNumber);
        sendData = new byte[UDPClient.PACKET_LENGTH];
        sendData = packet;

        // send the packet
        try {
            sendPacket(incomingPort, ipAddress);
//            sequenceNumber++;
        } catch (IOException e) {
            System.out.println("Unable to process response " + e.getLocalizedMessage());
        }
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


    //This would handle the conditionals inside LoadFileFromResponse
    private void handlePacketCondition() {

    }

    /**
     * Asks the socket ot listen for a null packet
     */
    private void listenForNullPacket() {
        try {
            receivePacket();
        } catch (SocketTimeoutException to) {
            return;
        } catch (IOException e) {
            System.out.println("Unable to receive terminating packet..." + e.getLocalizedMessage());
            return;
        }
    }

    /**
     * Called upon receipt of a null packet
     * Write the file buffer to file
     */
    private void receivedNullPacket() {
        try {
            UDPClient.clientSocket.setSoTimeout(0);
        } catch (Exception e) {
            return;
        }
        UDPClient.clientSocket.close();
        System.out.println("Received null packet from server... writing file");
        file = fileInfo.getBytes();
        if (fileInfo.getBytes().length > 0) {
            writeFile();
        }
    }

    /**
     * Write the data in the open file buffer to a file
     */
    private void writeFile() {
        File localFile = new File("./" + UDPClient.fileName);
        if (!localFile.isFile()) {
            // create file
            try {
                localFile.createNewFile();
            } catch (IOException e) {
                System.out.println("Unable to convert file path! " + e.getLocalizedMessage());
                return;
            }
        }

        try {
            // write file
            Files.write(localFile.toPath(), file);
            System.out.println("Wrote file successfully!");
            file = null;
        } catch (IOException e) {
            System.out.println("Unable to write to file! Error: " + e.getLocalizedMessage());
            return;
        }

    }

    /**
     * Asks the client to receive a new packet from the socket
     *
     * @throws IOException E if unable to receive packet
     */
    private void receivePacket() throws IOException {
        receiveData = new byte[UDPClient.PACKET_LENGTH]; // empty data buffer
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

        while (true) {
            try {
                UDPClient.clientSocket.receive(receivePacket);
                incomingPort = receivePacket.getPort();
            } catch (SocketTimeoutException to) {
                System.out.println("Error receiving packet... " + to.getLocalizedMessage());
                throw to;
            } catch (IOException e) {
                System.out.println("Error receiving packet... " + e.getLocalizedMessage());
                throw e;
            }

            if (receiveData[0] != 0) {
                return;
            } else if (receiveData[receiveData.length - 1] != 0) {
                return;
            } else {
                receivedNullPacket();
                return;
            }
        }
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
            UDPClient.clientSocket.send(sendPacket);
        } catch (IOException e) {
            System.out.println("Unable to send packet... " + e.getLocalizedMessage());
            throw e;
        }
    }

    /**
     * Tries to detect an error in the checksum in the header != the data received
     *
     * @param expectedChecksum int the checksum received in the packet header
     * @param packet           byte[] the packet to check against
     * @return
     */
    private boolean checksumErrorExists(int expectedChecksum, byte[] packet) {
        int checksum = 0;
        for (Byte b : packet) {
            checksum += b.intValue();
        }

        System.out.println("\nChecksum comparison finished. Expected: " + expectedChecksum + " actual: " + checksum);

        return checksum == expectedChecksum;
    }

    /**
     * Decide whether to damage a packet
     * Damage packet if necessary
     *
     * @param packet the packet to check
     * @return the changed (or not) packet
     */
    private byte[] gremlin(byte[] packet, int headerLength) {
        // First, decide whether to damage packet or not
        Random generator = new Random();
        double randomNumber = generator.nextDouble();

        if (randomNumber <= UDPClient.lostPacketProbability) {
            currentPacketCondition = PacketCondition.LOST;
            return null;
        }
        if (randomNumber <= UDPClient.delayedPacketProbability) {
            currentPacketCondition = PacketCondition.DELAYED;
            return packet;
        }

        if (randomNumber <= UDPClient.damagedPacketProbability) {
            currentPacketCondition = PacketCondition.DAMAGED;

            double randomProb = generator.nextDouble();

            // We decided to damage it
            // Let's determine how much to damage
            if (randomProb > 0.0 && randomProb <= 0.2) {
                int randomIdx1 = generator.nextInt(((packet.length - 1) - headerLength) + headerLength);
                int randomIdx2 = generator.nextInt(((packet.length - 1) - headerLength) + headerLength);
                int randomIdx3 = generator.nextInt(((packet.length - 1) - headerLength) + headerLength);
                packet[randomIdx1] = 0;
                packet[randomIdx2] = 0;
                packet[randomIdx3] = 0;
            } else if (randomProb > 0.2 && randomProb <= 0.5) {
                int randomIdx1 = generator.nextInt(((packet.length - 1) - headerLength) + headerLength);
                int randomIdx2 = generator.nextInt(((packet.length - 1) - headerLength) + headerLength);
                packet[randomIdx1] = 0;
                packet[randomIdx2] = 0;
            } else {
                int randomIdx = generator.nextInt(((packet.length - 1) - headerLength) + headerLength);
                packet[randomIdx] = 0;
            }

            return packet;
        }

        //Nothing happened to the packet, so return it.
        return packet;
    }

    /**
     * Trim a byte array of any null values
     *
     * @param bytes byte[] the byte array to trim
     * @return byte[] the trimmed array
     */
    private byte[] trim(byte[] bytes) {
        int i = bytes.length - 1;
        while (i >= 0 && bytes[i] == 0) {
            --i;
        }

        return Arrays.copyOf(bytes, i + 1);
    }

    private enum PacketCondition {
        DAMAGED, LOST, DELAYED, OKAY
    }

    private enum ResponseCodes {
        UNKNOWN, OKAY, NOT_FOUND
    }

    private ResponseCodes getResponseString(int response) {
        switch (response) {
            case 200:
                return ResponseCodes.OKAY;
            case 404:
                return ResponseCodes.NOT_FOUND;
            default:
                break;
        }

        return ResponseCodes.UNKNOWN;
    }
}
