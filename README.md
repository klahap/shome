# SHome

## PI setup

OS: Raspberry Pi OS Lite

install JRE

```bash
sudo apt install -y openjdk-25-jre-headless
```

logs nicht doppelt speichern

```ini
# /etc/systemd/journald.conf

Storage=volatile
RuntimeMaxUse=32M
```

Wi-Fi und BT deaktivieren

```ini
# /boot/firmware/config.txt

[all]
dtoverlay=disable-wifi
dtoverlay=disable-bt
```

service unit

```ini
# /etc/systemd/system/shome.service

[Unit]
Description=Shome Server
After=network.target
StartLimitIntervalSec=0

[Service]
Type=simple
User=admin
WorkingDirectory=/home/admin
ExecStart=/usr/bin/java -Xms128m -Xmx400m -Xss512k -XX:MaxMetaspaceSize=128m -XX:MaxDirectMemorySize=64m -XX:+UseSerialGC -XX:+UseCompactObjectHeaders -XX:+ExitOnOutOfMemoryError -XX:+PerfDisableSharedMem -jar /home/admin/server-all.jar --port 80 --jar-path /home/admin/server-all.jar
MemoryMax=750M
Environment=MALLOC_ARENA_MAX=2
Restart=always
RestartSec=5
AmbientCapabilities=CAP_NET_BIND_SERVICE
CapabilityBoundingSet=CAP_NET_BIND_SERVICE
NoNewPrivileges=true

[Install]
WantedBy=multi-user.target
```

activate

```bash
sudo systemctl daemon-reload
sudo systemctl enable shome.service
sudo systemctl start shome.service
```

usefully CMD's

```bash
sudo systemctl status shome              # is running?
sudo journalctl -u shome -f              # Logs live
sudo systemctl restart shome             # after JAR update
sudo systemctl show shome -p MemoryPeak  # memory peaks
```


## build and run

```bash
./gradlew server:installShadowDist

# copy to PI
scp server/build/install/server-shadow/lib/server-all.jar admin@192.168.178.128:~/
```