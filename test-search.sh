#!/bin/bash

# Test script for jOOQ MCP search functionality

echo "Testing jOOQ MCP Search Functionality"
echo "======================================"

# Base URL
BASE_URL="http://localhost:8080"

# Test 1: Search for "SELECT DISTINCT"
echo ""
echo "Test 1: Searching for 'SELECT DISTINCT'"
curl -X POST "$BASE_URL/mcp/v1/tools/call" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "searchDocumentation",
    "arguments": {
      "query": "SELECT DISTINCT"
    }
  }' | jq .

# Test 2: Search for "window functions"
echo ""
echo "Test 2: Searching for 'window functions'"
curl -X POST "$BASE_URL/mcp/v1/tools/call" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "searchDocumentation",
    "arguments": {
      "query": "window functions"
    }
  }' | jq .

# Test 3: Search for "INSERT RETURNING"
echo ""
echo "Test 3: Searching for 'INSERT RETURNING'"
curl -X POST "$BASE_URL/mcp/v1/tools/call" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "searchDocumentation",
    "arguments": {
      "query": "INSERT RETURNING"
    }
  }' | jq .

# Test 4: Search for "JOIN"
echo ""
echo "Test 4: Searching for 'JOIN'"
curl -X POST "$BASE_URL/mcp/v1/tools/call" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "searchDocumentation",
    "arguments": {
      "query": "JOIN"
    }
  }' | jq .