# SHome

## PI setup

install JRE

```bash
sudo apt install -y openjdk-25-jre-headless

# allow java to open ports < 1024
sudo setcap 'cap_net_bind_service=+ep' $(readlink -f $(which java))
```

service unit `sudo vim /etc/systemd/system/shome.service`

```text
[Unit]
Description=Shome Server
After=network.target

[Service]
Type=simple
WorkingDirectory=/home/admin
ExecStart=/usr/bin/java -Xms128m -Xmx400m -Xss512k -XX:MaxMetaspaceSize=128m -XX:MaxDirectMemorySize=64m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -jar /home/admin/server-all.jar --port 80
MemoryMax=700M
Restart=on-failure
RestartSec=5
User=admin

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
sudo systemctl status shome        # is running?
sudo journalctl -u shome -f        # Logs live
sudo systemctl restart shome       # after JAR update
```


## build and run

```bash
./gradlew server:installShadowDist

# copy to PI
scp server/build/install/server-shadow/lib/server-all.jar admin@192.168.0.103:~/

# run
java -jar server/build/install/server-shadow/lib/server-all.jar 
```