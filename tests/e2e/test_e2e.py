import unittest
import requests
import threading
import queue
import time

BASE_URL = "http://127.0.0.1:8080"

class TestPTUTicketE2E(unittest.TestCase):
    
    def setUp(self):
        try:
            resp = requests.post(f"{BASE_URL}/api/tickets/reset")
            self.assertEqual(resp.status_code, 200)
        except requests.exceptions.ConnectionError:
            self.fail("Could not connect to mock server. Is it running?")

    # ==========================================
    # Tier 1: Feature Coverage (Happy Path)
    # ==========================================
    
    # 5+ Query Tests
    def test_query_happy_path_adult(self):
        resp = requests.get(f"{BASE_URL}/api/tickets/query?fromStation=A&toStation=B&date=2026-06-18")
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertIsInstance(data, list)
        self.assertTrue(len(data) >= 1)
        ticket = data[0]
        self.assertEqual(ticket["fromStation"], "A")
        self.assertEqual(ticket["toStation"], "B")
        self.assertEqual(ticket["date"], "2026-06-18")
        self.assertIn("price", ticket)
        self.assertIn("stock", ticket)
        self.assertIn("type", ticket)

    def test_query_happy_path_child(self):
        resp = requests.get(f"{BASE_URL}/api/tickets/query?fromStation=A&toStation=B&date=2026-06-18")
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        child_tickets = [t for t in data if t["type"] == "CHILD"]
        self.assertTrue(len(child_tickets) >= 1)
        self.assertEqual(child_tickets[0]["price"], 120.0)

    def test_query_different_date(self):
        resp = requests.get(f"{BASE_URL}/api/tickets/query?fromStation=A&toStation=B&date=2026-06-19")
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertEqual(len(data), 1)
        self.assertEqual(data[0]["date"], "2026-06-19")

    def test_query_different_route(self):
        resp = requests.get(f"{BASE_URL}/api/tickets/query?fromStation=B&toStation=C&date=2026-06-18")
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertEqual(len(data), 1)
        self.assertEqual(data[0]["fromStation"], "B")
        self.assertEqual(data[0]["toStation"], "C")

    def test_query_no_results(self):
        resp = requests.get(f"{BASE_URL}/api/tickets/query?fromStation=A&toStation=C&date=2026-06-19")
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertEqual(data, [])

    # 5+ Order Tests
    def test_order_happy_path_adult(self):
        payload = {"userId": 1001, "ticketId": 1, "passengerType": "ADULT"}
        resp = requests.post(f"{BASE_URL}/api/tickets/order", json=payload)
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertTrue(data["success"])
        self.assertTrue(data["orderNo"].startswith("ORD"))
        self.assertEqual(data["message"], "Order is processing")

    def test_order_happy_path_child(self):
        payload = {"userId": 1002, "ticketId": 2, "passengerType": "CHILD"}
        resp = requests.post(f"{BASE_URL}/api/tickets/order", json=payload)
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertTrue(data["success"])

    def test_order_happy_path_student(self):
        payload = {"userId": 1003, "ticketId": 6, "passengerType": "STUDENT"}
        resp = requests.post(f"{BASE_URL}/api/tickets/order", json=payload)
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertTrue(data["success"])

    def test_order_different_user(self):
        payload = {"userId": 9999, "ticketId": 1, "passengerType": "ADULT"}
        resp = requests.post(f"{BASE_URL}/api/tickets/order", json=payload)
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertTrue(data["success"])

    def test_order_different_ticket(self):
        payload = {"userId": 1001, "ticketId": 5, "passengerType": "ADULT"}
        resp = requests.post(f"{BASE_URL}/api/tickets/order", json=payload)
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertTrue(data["success"])

    # ==========================================
    # Tier 2: Boundary & Corner Cases
    # ==========================================
    
    # 5+ Query Tests
    def test_query_missing_from_station(self):
        resp = requests.get(f"{BASE_URL}/api/tickets/query?toStation=B&date=2026-06-18")
        self.assertEqual(resp.status_code, 400)

    def test_query_missing_to_station(self):
        resp = requests.get(f"{BASE_URL}/api/tickets/query?fromStation=A&date=2026-06-18")
        self.assertEqual(resp.status_code, 400)

    def test_query_missing_date(self):
        resp = requests.get(f"{BASE_URL}/api/tickets/query?fromStation=A&toStation=B")
        self.assertEqual(resp.status_code, 400)

    def test_query_empty_from_station(self):
        resp = requests.get(f"{BASE_URL}/api/tickets/query?fromStation= &toStation=B&date=2026-06-18")
        self.assertEqual(resp.status_code, 400)

    def test_query_invalid_date_format(self):
        resp = requests.get(f"{BASE_URL}/api/tickets/query?fromStation=A&toStation=B&date=18-06-2026")
        self.assertEqual(resp.status_code, 400)

    def test_query_invalid_calendar_date(self):
        resp = requests.get(f"{BASE_URL}/api/tickets/query?fromStation=A&toStation=B&date=2026-02-30")
        self.assertEqual(resp.status_code, 400)

    # 5+ Order Tests
    def test_order_missing_userId(self):
        payload = {"ticketId": 1, "passengerType": "ADULT"}
        resp = requests.post(f"{BASE_URL}/api/tickets/order", json=payload)
        self.assertEqual(resp.status_code, 400)

    def test_order_missing_ticketId(self):
        payload = {"userId": 1001, "passengerType": "ADULT"}
        resp = requests.post(f"{BASE_URL}/api/tickets/order", json=payload)
        self.assertEqual(resp.status_code, 400)

    def test_order_missing_passengerType(self):
        payload = {"userId": 1001, "ticketId": 1}
        resp = requests.post(f"{BASE_URL}/api/tickets/order", json=payload)
        self.assertEqual(resp.status_code, 400)

    def test_order_invalid_user_id_negative(self):
        payload = {"userId": -1, "ticketId": 1, "passengerType": "ADULT"}
        resp = requests.post(f"{BASE_URL}/api/tickets/order", json=payload)
        self.assertEqual(resp.status_code, 400)

    def test_order_invalid_ticket_id_negative(self):
        payload = {"userId": 1001, "ticketId": -1, "passengerType": "ADULT"}
        resp = requests.post(f"{BASE_URL}/api/tickets/order", json=payload)
        self.assertEqual(resp.status_code, 400)

    def test_order_nonexistent_ticket(self):
        payload = {"userId": 1001, "ticketId": 9999, "passengerType": "ADULT"}
        resp = requests.post(f"{BASE_URL}/api/tickets/order", json=payload)
        self.assertEqual(resp.status_code, 400)

    def test_order_out_of_stock(self):
        payload = {"userId": 1001, "ticketId": 4, "passengerType": "ADULT"}
        resp = requests.post(f"{BASE_URL}/api/tickets/order", json=payload)
        self.assertEqual(resp.status_code, 400)

    # ==========================================
    # Tier 3: Cross-Feature Combinations
    # ==========================================
    
    def test_query_order_query_stock_reduction(self):
        resp = requests.get(f"{BASE_URL}/api/tickets/query?fromStation=A&toStation=B&date=2026-06-19")
        self.assertEqual(resp.status_code, 200)
        data = resp.json()
        self.assertEqual(len(data), 1)
        initial_stock = data[0]["stock"]
        ticket_id = data[0]["id"]
        
        payload = {"userId": 1001, "ticketId": ticket_id, "passengerType": "ADULT"}
        order_resp = requests.post(f"{BASE_URL}/api/tickets/order", json=payload)
        self.assertEqual(order_resp.status_code, 200)
        self.assertTrue(order_resp.json()["success"])
        
        resp_after = requests.get(f"{BASE_URL}/api/tickets/query?fromStation=A&toStation=B&date=2026-06-19")
        self.assertEqual(resp_after.status_code, 200)
        data_after = resp_after.json()
        self.assertEqual(data_after[0]["stock"], initial_stock - 1)

    def test_order_bad_id_query_no_change(self):
        resp = requests.get(f"{BASE_URL}/api/tickets/query?fromStation=A&toStation=B&date=2026-06-19")
        initial_stock = resp.json()[0]["stock"]
        
        payload = {"userId": 1001, "ticketId": 9999, "passengerType": "ADULT"}
        order_resp = requests.post(f"{BASE_URL}/api/tickets/order", json=payload)
        self.assertEqual(order_resp.status_code, 400)
        
        resp_after = requests.get(f"{BASE_URL}/api/tickets/query?fromStation=A&toStation=B&date=2026-06-19")
        self.assertEqual(resp_after.json()[0]["stock"], initial_stock)

    def test_concurrent_orders_different_tickets(self):
        results = []
        def place_order(ticket_id):
            payload = {"userId": 1000 + ticket_id, "ticketId": ticket_id, "passengerType": "ADULT"}
            resp = requests.post(f"{BASE_URL}/api/tickets/order", json=payload)
            results.append(resp.status_code)
            
        t1 = threading.Thread(target=place_order, args=(1,))
        t2 = threading.Thread(target=place_order, args=(2,))
        
        t1.start()
        t2.start()
        t1.join()
        t2.join()
        
        self.assertEqual(sorted(results), [200, 200])
        
        resp1 = requests.get(f"{BASE_URL}/api/tickets/query?fromStation=A&toStation=B&date=2026-06-18")
        tickets = resp1.json()
        t1_stock = [t for t in tickets if t["id"] == 1][0]["stock"]
        t2_stock = [t for t in tickets if t["id"] == 2][0]["stock"]
        self.assertEqual(t1_stock, 49)
        self.assertEqual(t2_stock, 9)

    def test_order_fail_stock_not_decremented(self):
        payload = {"userId": 1001, "ticketId": 4, "passengerType": "ADULT"}
        order_resp = requests.post(f"{BASE_URL}/api/tickets/order", json=payload)
        self.assertEqual(order_resp.status_code, 400)
        
        resp = requests.get(f"{BASE_URL}/api/tickets/query?fromStation=B&toStation=C&date=2026-06-18")
        self.assertEqual(resp.json()[0]["stock"], 0)

    # ==========================================
    # Tier 4: Real-World Scenarios
    # ==========================================
    
    def test_complete_booking_workflow(self):
        resp = requests.get(f"{BASE_URL}/api/tickets/query?fromStation=A&toStation=B&date=2026-06-19")
        self.assertEqual(resp.status_code, 200)
        tickets = resp.json()
        self.assertEqual(len(tickets), 1)
        ticket = tickets[0]
        initial_stock = ticket["stock"]
        
        for i in range(initial_stock):
            payload = {"userId": 2000 + i, "ticketId": ticket["id"], "passengerType": "ADULT"}
            order_resp = requests.post(f"{BASE_URL}/api/tickets/order", json=payload)
            self.assertEqual(order_resp.status_code, 200)
            self.assertTrue(order_resp.json()["success"])
            
        payload = {"userId": 3000, "ticketId": ticket["id"], "passengerType": "ADULT"}
        failed_order_resp = requests.post(f"{BASE_URL}/api/tickets/order", json=payload)
        self.assertEqual(failed_order_resp.status_code, 400)
        self.assertIn("out of stock", failed_order_resp.json()["detail"].lower())

    def test_concurrent_booking_depletion(self):
        ticket_id = 6
        num_threads = 10
        barrier = threading.Barrier(num_threads)
        results = queue.Queue()
        
        def attempt_booking(user_id):
            payload = {"userId": user_id, "ticketId": ticket_id, "passengerType": "STUDENT"}
            barrier.wait()
            try:
                resp = requests.post(f"{BASE_URL}/api/tickets/order", json=payload)
                results.put((resp.status_code, resp.json()))
            except Exception as e:
                results.put((500, str(e)))
                
        threads = []
        for i in range(num_threads):
            t = threading.Thread(target=attempt_booking, args=(4000 + i,))
            threads.append(t)
            t.start()
            
        for t in threads:
            t.join()
            
        success_count = 0
        failure_count = 0
        
        while not results.empty():
            status_code, body = results.get()
            if status_code == 200 and body.get("success") is True:
                success_count += 1
            else:
                failure_count += 1
                
        self.assertEqual(success_count, 1)
        self.assertEqual(failure_count, num_threads - 1)
        
        resp = requests.get(f"{BASE_URL}/api/tickets/query?fromStation=A&toStation=B&date=2026-06-18")
        tickets = resp.json()
        t6 = [t for t in tickets if t["id"] == ticket_id][0]
        self.assertEqual(t6["stock"], 0)

if __name__ == "__main__":
    unittest.main()
