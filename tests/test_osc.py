import unittest
from osc_rta import osc_message, decode_osc


class OscTests(unittest.TestCase):
    def test_identity_query(self):
        self.assertEqual(decode_osc(osc_message("/xinfo")), ("/xinfo", []))

    def test_meter_subscription(self):
        self.assertEqual(
            decode_osc(osc_message("/meters", "/meters/15")),
            ("/meters", ["/meters/15"]),
        )


if __name__ == "__main__":
    unittest.main()
