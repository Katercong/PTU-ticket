import subprocess
import time
import sys
import os
import requests
import unittest

def main():
    dir_path = os.path.dirname(os.path.abspath(__file__))
    server_path = os.path.join(dir_path, "mock_server.py")
    
    print(f"Starting mock server: {server_path}")
    server_proc = subprocess.Popen([sys.executable, server_path])
    
    try:
        url = "http://127.0.0.1:8080/api/tickets/reset"
        started = False
        for i in range(50):
            try:
                resp = requests.post(url)
                if resp.status_code == 200:
                    started = True
                    break
            except requests.exceptions.ConnectionError:
                pass
            time.sleep(0.1)

        if not started:
            print("Error: Failed to start mock server within 5 seconds.")
            sys.exit(1)

        print("Mock server is ready. Running E2E tests...")
        
        loader = unittest.TestLoader()
        suite = loader.discover(start_dir=dir_path, pattern="test_e2e.py")
        runner = unittest.TextTestRunner(verbosity=2)
        result = runner.run(suite)
        
        if result.wasSuccessful():
            print("E2E Test Run: SUCCESS")
            exit_code = 0
        else:
            print("E2E Test Run: FAILURE")
            exit_code = 1
            
    finally:
        print("Terminating mock server process...")
        server_proc.terminate()
        try:
            server_proc.wait(timeout=5)
            print("Mock server process terminated gracefully.")
        except subprocess.TimeoutExpired:
            print("Mock server process failed to terminate gracefully. Killing it...")
            server_proc.kill()
            server_proc.wait()
            
    sys.exit(exit_code)

if __name__ == "__main__":
    main()
