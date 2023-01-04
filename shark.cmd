REM use CNTRL Z to gracefully exit this please
mkdir logs
tshark -w logs/nlog.pcap -i "Ethernet 6" -o "tls.keylog_file:logs/sslkeys.log"
