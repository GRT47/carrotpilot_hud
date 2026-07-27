import socket
import threading
import json
import logging

logger = logging.getLogger(__name__)

class UdpReceiver:
    def __init__(self, host='0.0.0.0', port=5005):
        self.host = host
        self.port = port
        self.socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        # Non-blocking is not strictly needed if we run in a separate thread,
        # but using a timeout allows the thread to check `self.running` and exit cleanly.
        self.socket.settimeout(1.0)
        
        self.latest_data = None
        self.running = False
        self.thread = None

    def start(self):
        if self.running:
            return
        
        try:
            self.socket.bind((self.host, self.port))
            self.running = True
            self.thread = threading.Thread(target=self._run_loop, daemon=True)
            self.thread.start()
            logger.info(f"UDP Receiver started on {self.host}:{self.port}")
        except Exception as e:
            logger.error(f"Failed to start UDP Receiver: {e}")

    def stop(self):
        self.running = False
        if self.thread:
            self.thread.join(timeout=2.0)
        self.socket.close()

    def _run_loop(self):
        while self.running:
            try:
                data, addr = self.socket.recvfrom(65535)
                decoded = data.decode('utf-8')
                try:
                    # Attempt to parse as JSON for structured data
                    self.latest_data = json.loads(decoded)
                except json.JSONDecodeError:
                    # Fallback to plain string if it's not JSON
                    self.latest_data = {"raw_text": decoded}
            except socket.timeout:
                continue
            except Exception as e:
                if self.running:
                    logger.error(f"Error receiving UDP data: {e}")

    def get_latest_data(self):
        """Returns the most recently received data, or None if nothing received yet."""
        return self.latest_data
