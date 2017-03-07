#include <stdio.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <string.h>
#include <unistd.h>
#include <sys/types.h>
#include <arpa/inet.h>
#include <fcntl.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <stdlib.h>
#include <time.h>


#define FILENAME "test_file"
#define PACKET_SIZE 128
FILE *fp;
int clientSocket;
char buffer[1024];
//time_t start, end;
struct sockaddr_in serverAddr;
	  socklen_t addr_size;
 struct stat file_status;
 int damage;
int packetnum;



int connect_to_server()
{

	printf("Requesting server authentication\n");
		  /*---- Create the socket. The three arguments are: ----*/
		  /* 1) Internet domain 2) Stream socket 3) Default protocol (TCP in this case) */
		  clientSocket = socket(PF_INET, SOCK_STREAM, 0);

		  //setup timer
		  struct timeval tv;
		  tv.tv_sec = 0;
		  tv.tv_usec = 20000;
		  setsockopt(clientSocket,SOL_SOCKET, SO_RCVTIMEO, (char *)&tv, sizeof(struct timeval));

		  /*---- Configure settings of the server address struct ----*/
		  /* Address family = Internet */
		  serverAddr.sin_family = AF_INET;
		  /* Set port number, using htons function to use proper byte order */
		  serverAddr.sin_port = htons(7891);
		  /* Set IP address to localhost */
		  serverAddr.sin_addr.s_addr = inet_addr("127.0.0.1");
		  /* Set all bits of the padding field to 0 */
		  memset(serverAddr.sin_zero, '\0', sizeof serverAddr.sin_zero);

		  /*---- Connect the socket to the server using the address struct ----*/
		  addr_size = sizeof serverAddr;
		  connect(clientSocket, (struct sockaddr *) &serverAddr, addr_size);

		  printf("Authenticated by Server\n");
return 0;
}

/*int sendmessage()
{

	strcpy(buffer,"Hello World\n");
	send(clientSocket,buffer,1024,0);
			//  ---- Read the message from the server into the buffer ----
	//---- Print the received message ----
	  printf("Data received: %s",buffer);
	  return 0;

}*/

int readfile()
{
	if (stat(FILENAME, &file_status) != 0)
	{
		perror("ERROR: Could not stat or file does not exist");
	}

	printf("\nFileName: %s\n", FILENAME);
	printf("FileSize: %9jd \n\n", ((intmax_t)file_status.st_size));

	fp = fopen(FILENAME, "r");

	/*int sof = file_status.st_size;
	//int num = sof;
	//int size;
	//while (sof != 0)
	//{
		//size++;
		//num = num/10;
	//}
	char sof_buf[PACKET_SIZE];
	sprintf(sof_buf, "%d", sof);
	//sprintf(sof_buf, "1124");
	//strcpy(sof_buf,sof);
	printf("SIZE %c", sof_buf);*/
	//send(clientSocket,sof_buf,PACKET_SIZE,0);


	char *buffer = NULL;
	buffer = (char *)malloc(file_status.st_size +1);

	if( !fread(buffer, file_status.st_size, 1, fp))
	{
		perror("ERROR: Could not read file");
	}

	buffer[file_status.st_size +1] = '\0';
	createpacket();

	fclose(fp);
	free(buffer);
	return 0;
}


int createpacket()
{
		int loss;
		char msg[PACKET_SIZE];
	    char c, seqNum;
	    fp = fopen(FILENAME, "r");

	    seqNum = '0';
	    int characters_read = 0;
	    int position_in_buffer = 11;

	    //create checksum
	    	msg[1] = '7'; // first checksum
	        msg[2] = '7'; // second checksum
	        msg[3] = '7';
	        msg[4] = '7';
	        msg[5] = '7';
	        msg[6] = '7';
	        msg[7] = '7';
	        msg[8] = '7';
	        msg[9] = '0'; // Remainder if dived by 7
	        msg[10] = 'A'; // or NACK Needs to be changed

	        packetnum = 1;
	        // FILL DATA FROM FILE/SEND MESSAGE*************************************
	            while( (c=fgetc(fp)) != EOF )
	            {
	            	if (characters_read == 0){
	            		printf("\nWriting Data to Packet %d\n", packetnum);
	            	}
	                msg[0] = seqNum; // sequence number could be 0 or 1
	                characters_read++;
	                msg[position_in_buffer]=c;
	                position_in_buffer++;


	                /* we just hit 100 bytes. send the packet off */
	                if(characters_read==116)
	                {
	                	printf("Finished Filling Packet %d\n", packetnum);
	                    printf("Message Reads:\n%s\n(%lu bytes).\n", msg, sizeof(msg));
	                	printf("Sending Through Gremlin\n");
	                	loss = gremlin(msg);
	                	printf("Packet %d Sent\n\n\n", packetnum);
	                	if (loss == 3) {
	                		printf("TimeOut!!!!\n");
	                		printf("Packet Resent");
	                		send(clientSocket,msg,PACKET_SIZE,0);
	                	} else {
		                	send(clientSocket,msg,PACKET_SIZE,0);
	                	}
	                    characters_read=0;
	                    position_in_buffer = 11;
	                    //if(recv(clientSocket, msg, PACKET_SIZE, 0)== -1)
	                    //{
	                    	//printf("\nTimed Out...resending packet!\n");
	                   // 	while((recv(clientSocket, msg, PACKET_SIZE, 0)== -1)){
	                    recv(clientSocket, msg, PACKET_SIZE, 0);
	                   // 	}

	                    //}
	                    while(msg[0] == '1'){
	                    	packetnum++;
	                    	printf("\nPacket Corrupted Caught\n");
	                    	msg[0] = '0';
	            	    	msg[1] = '7'; // first checksum
	            	        msg[2] = '7'; // second checksum
	            	        msg[3] = '7';
	            	        msg[4] = '7';
	            	        msg[5] = '7';
	            	        msg[6] = '7';
	            	        msg[7] = '7';
	            	        msg[8] = '7';
	            	        msg[9] = '0'; // Remainder if dived by 7
	            	        msg[10] = 'A'; // or NACK Needs to be changed
		                	//reset characters_read and position_in_buffer
	                    	printf("Finished Filling Packet %d\n", packetnum);
	                    	printf("Message Reads:\n%s\n(%lu bytes).\n", msg, sizeof(msg));
	                    	printf("Sending Through Gremlin\n");
	            	        gremlin(msg);
	                    	send(clientSocket,msg,PACKET_SIZE,0);
		                	printf("Packet %d Sent\n\n\n", packetnum);
		                    // receiving and increasing packet number
		                    recv(clientSocket, msg, PACKET_SIZE, 0);
	                    }
	                    packetnum++;
	                }
	            }
	            if( (position_in_buffer!=11) || (position_in_buffer!=127) )
	            {
	                int i;
	                int count_null_characters = 0;
	                for(i = PACKET_SIZE-1;i> (position_in_buffer-1); i--)
	                {
	                    count_null_characters++;
	                    msg[i]='\0';
	                }

	                if(count_null_characters) /* In c anything except 0 is true */
	                {
	                	msg[127] = '*';
	                	printf("Not Enough Data to Completely Fill Packet\n");
	                	printf("Inserted %i null characters to pad the packet\n",count_null_characters);
	                	printf("Last Packet Reads:\n");
	                	send(clientSocket,msg,PACKET_SIZE,0);
	                	printf("%s", msg);
	                	printf("\nPacket %d Sent\n\n\n", packetnum);
	                }
	            }

	    fclose(fp);
	    return 0;

}

int gremlin(char data[])
{
	int r, degree, dataloss;
	dataloss = rand() % 10;
	r = rand() % 10;
	int i = 1;
	//printf("Damage will be %d", damage);
	//printf("\nThis is the data %c\n",data[i]);
	//if (r <= 2){
	//	printf
	//}
	if(r <= damage)
	{
		degree = rand() % 10;
		if(degree <= 7)
		{
			data[1] = '8';
			printf("One Bit Has Been Damaged\n");
		}
		else if((degree > 7) & (degree < 10))
		{

			data[1] = '8';
			data[2] = '8';
			printf("Two Bits Have Been Damaged\n");

		}
		else
		{
			data[1] = '8';
			data[2] = '8';
			data[3] = '8';
			printf("Three Bits Have Been Damaged\n");
		}
		//printf("%d", degree);
	}
	if (dataloss <= 2)
	{
		return 3;
	}
	//printf("This is random value %d\n", r);
	return 0;//data;
}

void askfordamage()
{
printf("Type a number for damage: ");
scanf("%d", &damage);
printf("\nThe value we are using for damage is %d", damage);
printf("\nPacket Loss Value is 2");
}





int main(){
	connect_to_server();
	askfordamage();
	readfile();

  return 0;
}

