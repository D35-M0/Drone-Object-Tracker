import socket
# from bitarray import bitarray
# import Image
# import io
# import cv2
# import png
import locale
import struct
import StringIO

# locale.setlocale(locale.LC_ALL, 'en_US.UTF-8')


server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

# Get information on socket to establish and attempts to create it
net_string = raw_input("Enter self socket info (<ip address>:<port number>): ")
socket_info = net_string.split(":")

try:
    ipAdd = socket_info[0]
    ipPort = int(socket_info[1])
    server_socket.bind((ipAdd, ipPort))
except socket.error:
    print
    "Socket already connected"
    pass
server_socket.listen(5)

while True:
    # Listens for connection from phone
    try:
        (client_socket, address) = server_socket.accept()
        print
        "Connected to: ", address, "\n"
    except socket.error as e:
        print("Error establishing a connection: " + e.message)
        pass
    client_socket.settimeout(1)

    timeout_hit = False

    # size = 0
    while not timeout_hit:
        try:

            print
            "reading png"
            message = ''
            full_thing = ''

            # The image is sent in chunks, so we need to iterate over the sockets
            # and get all of the chunks
            while True:
                try:
                    # Get a chunk
                    message = client_socket.recv(1024)
                    # There will be a note attached to the end of the last
                    # chunk saying that we are done and can break
                    if "DONE" in message:
                        message = message[:-4]
                        full_thing += message
                        break
                    # Add the chunk to the full picture
                    full_thing += message
                except socket.error:
                    print
                    "SOMETHING WENt HORRIBLY WRONG GETTING PICTURE"
                    timeout_hit = True
                    server_socket.close()
                    break

            if not timeout_hit:
                # Open a new png photo and write the image (full_thing) to the file
                with open("/home/cameron/PycharmProjects/Drone-Controller/test.jpg", 'wb') as f:
                    f.write(full_thing)

                    # TODO: Show "video" of live image updates
                    # cv2.imshow("We're being hopeful", "/home/robolab/PycharmProjects/DroneImageTest/test.png")
        except Exception:
            server_socket.close()
            print
            "THINGS HAPPENED"
            break

client_socket.close()
server_socket.close()
