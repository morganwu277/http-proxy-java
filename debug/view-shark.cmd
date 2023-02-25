REM use CNTRL Z to gracefully exit this please
mkdir logs
wireshark -r logs/nlog.pcap -o "tls.keylog_file:logs/sslkeys.log"
