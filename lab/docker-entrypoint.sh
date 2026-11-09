#!/bin/sh
# Inside Docker, Asterisk sits behind a NAT of its own: it must advertise the address phones reach
# it at, or audio and hang-ups go to the container's address, which no phone can reach.
# EXTERNAL_ADDRESS is that address: 10.0.2.2 for the Android emulator, this computer's LAN address
# for a real phone. Only the Docker lab gets this file; a real server needs none of it.
# No local_net: Docker delivers every phone's packets from its own gateway, which a local_net
# would cover, and Asterisk would then advertise its container address to everyone.
ADDR="${EXTERNAL_ADDRESS:-10.0.2.2}"

cat > /etc/asterisk/pjsip_docker.conf <<CONF
[transport-udp](+)
external_media_address=$ADDR
external_signaling_address=$ADDR

[transport-tcp](+)
external_media_address=$ADDR
external_signaling_address=$ADDR
CONF

echo "Advertising $ADDR to phones (set EXTERNAL_ADDRESS to change it)"
exec asterisk -f -vvv
