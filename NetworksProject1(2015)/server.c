/****************** SERVER CODE ****************/

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

#define PACKET_SIZE 128
int welcomeSocket, newSocket;
char buffer[1024];
struct sockaddr_in serverAddr;
struct sockaddr_storage serverStorage;
socklen_t addr_size;
FILE *ofp;

int connect_to_client()
{
	/*---- Create the socket. The three arguments are: ----*/
		  /* 1) Internet domain 2) Stream socket 3) Default protocol (TCP in this case) */
		  welcomeSocket = socket(PF_INET, SOCK_STREAM, 0);

		  /*---- Configure settings of the server address struct ----*/
		  /* Address family = Internet */
		  serverAddr.sin_family = AF_INET;
		  /* Set port number, using htons function to use proper byte order */
		  serverAddr.sin_port = htons(7891);
		  /* Set IP address to localhost */
		  serverAddr.sin_addr.s_addr = inet_addr("127.0.0.1");
		  /* Set all bits of the padding field to 0 */
		  memset(serverAddr.sin_zero, '\0', sizeof serverAddr.sin_zero);

		  /*---- Bind the address struct to the socket ----*/
		  bind(welcomeSocket, (struct sockaddr *) &serverAddr, sizeof(serverAddr));

		  /*---- Listen on the socket, with 5 max connection requests queued ----*/
		  if(listen(welcomeSocket,5)==0)
		    printf("Listening\n");

		  else
		    printf("Error\n");

		  /*---- Accept call creates a new socket for the incoming connection ----*/
		  addr_size = sizeof serverStorage;
		  newSocket = accept(welcomeSocket, (struct sockaddr *) &serverStorage, &addr_size);

		  connect(newSocket, (struct sockaddr *) &serverStorage, addr_size);
		  return 0;
}


int receivemessage()
{

	//char sof[PACKET_SIZE];
	//recv(newSocket, sof, PACKET_SIZE, 0);
//printf("Size of File %c", sof);

	ofp=fopen("write_file", "w");
	if(ofp ==NULL)
	{
		printf("Can't open output file");
		exit(1);
	}
	  char c;
	  int packetCount = 1;
	  while (c != '*'){
		  int passed;

		  recv(newSocket, buffer, PACKET_SIZE, 0);

		  //used to test the time out
		  //sleep(1);

		  printf("\nReceived Packet %d\n", packetCount);
		  packetCount++;
		  printf("Checked Packet For Errors\n");
		  passed = calculateCheckSum(buffer);
		  if (passed == 1)
		  {
			  printf("Packet Valid\n");
			  int i;
			  for(i=11; i<PACKET_SIZE; i++)
			  {
				  fputc(buffer[i], ofp);
				  c = buffer[i];
			  }

			  //c = buffer[10];
			  send(newSocket,buffer,PACKET_SIZE,0);
		  }
		  else {
			  printf("Packet Corrupted\n");
			  buffer[0] = '1';
			  send(newSocket,buffer,PACKET_SIZE,0);
		  }
	  }
	  fclose(ofp);
	return 0;
}

int calculateCheckSum(char packet[])
{

	int sum = 0;
	for(int i = 1; i <11; i++)
	{
		sum = packet[i] + sum;
	}
	if (sum % 7 == 0)
	{
		return 1;
	}
	else
	{
	return 0;
	}
}

void printOutput(){
	ofp=fopen("write_file", "r");
	if(ofp == NULL)
	{
		printf("Can't open output file");
		exit(1);
	}
	char c;
	fseek(ofp, 0L, SEEK_END);
	int size = ftell(ofp);
	fseek(ofp, 0L, SEEK_SET);
	printf("\n\nFile From Client Reads: \n");
	int pos = 0;
    while( (c=fgetc(ofp)) != EOF ){
    	if (pos < size -1)
    		printf("%c", c);
    	pos++;
    }

    fclose(ofp);
}

int main(){
  connect_to_client();
  receivemessage();
  printOutput();




  return 0;
}
