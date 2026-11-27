#!/usr/bin/env bash
# End-to-end demo: bank gets a token, announces a call, prints the voice-agent link.
# Needs: bash, curl. (Works in Git Bash on Windows.)
set -euo pipefail

BASE="${BASE:-http://localhost:8080}"
CLIENT_ID="${CLIENT_ID:-demo-bank}"
CLIENT_SECRET="${CLIENT_SECRET:-demo-secret}"
CUSTOMER="${CUSTOMER:-cust-001}"

json_field() { sed -n "s/.*\"$1\":\"\([^\"]*\)\".*/\1/p"; }

echo "1. Bank backend gets an OAuth access token (client credentials)"
TOKEN=$(curl -s -u "$CLIENT_ID:$CLIENT_SECRET" \
  -d grant_type=client_credentials -d "scope=calls.write calls.read" \
  "$BASE/oauth2/token" | json_field access_token)
[ -n "$TOKEN" ] || { echo "No token. Is the app running?"; exit 1; }

echo "2. Bank announces the call (make sure the bank-app page is connected as $CUSTOMER)"
RESPONSE=$(curl -s -X POST "$BASE/v1/call-intents" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(date +%s%N)" \
  -d "{\"customerRef\":\"$CUSTOMER\",\"msisdn\":\"+919800000001\",\"purpose\":\"EMI_REMINDER\",\"agentId\":\"agent-emi-01\"}")
echo "$RESPONSE"

INTENT=$(echo "$RESPONSE" | json_field intentId)
CODE=$(echo "$RESPONSE" | json_field spokenCode)
[ -n "$INTENT" ] || { echo "Intent was not created."; exit 1; }

echo
echo "3. Open the voice agent and click 'Start call':"
echo "   $BASE/voice-agent.html#intent=$INTENT&code=$CODE&token=$TOKEN"
echo
echo "4. After answering in the bank app, check the audit chain:"
echo "   curl -s -H \"Authorization: Bearer $TOKEN\" $BASE/v1/audit/verify"
