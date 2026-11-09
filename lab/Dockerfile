# Asterisk from Ubuntu's own package: no third-party image. For development and the call matrix;
# the course's server is set up from the same files in asterisk/.
FROM ubuntu:24.04

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends asterisk \
    && rm -rf /var/lib/apt/lists/*

COPY asterisk/ /etc/asterisk/
# RTP within the range the compose file publishes.
RUN printf '[general]\nrtpstart=10000\nrtpend=10100\n' > /etc/asterisk/rtp.conf

EXPOSE 5060/udp 5060/tcp 10000-10100/udp
CMD ["asterisk", "-f", "-vvv"]
