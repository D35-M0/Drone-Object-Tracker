import socket


class PictureGatherer:

    """
    A class designed to take in pictures from a socket and read them to a file

    :param ipaddr: ip address to connect to (typically IP of host machine)
    :param ipport: port to establish connection on (Usually use 6540)
    :param pic_location: path to picture to write phone contents to (location
            must include name and extension of picture)
    """
    def __init__(self, ipaddr, ipport, pic_location):
        self.ipaddr = ipaddr
        self.ipPort = ipport
        self.pic_location = pic_location

        # Attempts to create a socket at the given location and port.
        # If one is already established, just pass and uses that instead
        try:
            self.server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.server_socket.bind((ipaddr, ipport))
            self.server_socket.listen(3)
        except socket.error:
            print("Socket already connected")
            pass

        self.allowed_to_run = True

    """
    Establishes a connection with the phone and collects pictures
    until the connection breaks, times out, or stop_collection is called
    :param timeout: how long to let the connection hang before deciding to break
               it off, defaults to 1 second
    """
    def collect_pic(self, timeout=1):
        while self.allowed_to_run:
            # Listens for connection from phone
            try:
                (client_socket, address) = self.server_socket.accept()
                print("Connected to: " + str(address) + "\n")
            except socket.error as e:
                print("Error establishing a connection: " + str(e))

                pass

            client_socket.settimeout(timeout)

            # Flag for if the connection gets reset by peer or times out
            conn_closed = False

            while not conn_closed and self.allowed_to_run:

                size = client_socket.recv(1024)

                if size.isdigit():
                    client_socket.send("READY\n".encode())

                    size = int(size)

                    full_thing = b''
                    numbytesread = 0
                    # The image is sent in chunks, so we need to iterate over the sockets
                    # and get all of the chunks
                    while numbytesread < size:
                        try:
                            # Get a chunk
                            message = client_socket.recv(1024)
                            print(type(message))
                            # full_thing += message
                            # There will be a note attached to the end of the last
                            # chunk saying that we are done and can break
                            # CAN'T COMPARE STRING LITERAL TO BYTES. FIND ANOTHER METHOD
                            # if message.find(test):
                            #     message = message[:-4]
                            #     full_thing = b''.join([full_thing, message])
                            #     with open(self.pic_location, 'wb') as f:
                            #         f.write(full_thing)
                            #     break
                            # Add the chunk to the full picture
                            full_thing += message
                        except socket.error as e:
                            print("Error on connection reached: " + str(e))
                            conn_closed = True
                            self.server_socket.close()
                            break

                        except Exception as e:
                            print("Something went horribly, horribly wrong: " + str(e))
                            conn_closed = True
                            self.server_socket.close()
                            break

                    with open(self.pic_location, 'wb') as f:
                        f.write(full_thing)

                    client_socket.send("DONE\n".encode())
                # else:
                    # WHYYYYYYYYYYYYY
                #     print("err")

                    # if not conn_closed
                    #     # Open a new png photo and write the image (full_thing) to the file
                    #     with open(self.pic_location, 'wb') as f:
                    #         f.write(full_thing)

        client_socket.close()

    # Stops picture collection and closes server socket
    def stop_collection(self):
        self.allowed_to_run = False
        self.server_socket.close()
