import socket
import time
import json

def main():
    host = '127.0.0.1'
    port = 5005
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    
    print(f"Sending test UDP packets to {host}:{port}...")
    
    rpm = 800
    temp = 85
    
    try:
        while True:
            data = {
                "Engine RPM": f"{rpm}",
                "Coolant Temp": f"{temp} C",
                "Status": "Normal",
                "media_title": "Supernova",
                "media_artist": "aespa"
            }
            
            message = json.dumps(data).encode('utf-8')
            sock.sendto(message, (host, port))
            
            print(f"Sent: {data}")
            
            rpm = (rpm + 50) % 7000
            if rpm < 800: rpm = 800
            
            time.sleep(1.0)
    except KeyboardInterrupt:
        print("\nStopped.")

if __name__ == "__main__":
    main()
