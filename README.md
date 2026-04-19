# On-Call Agent (Spring Boot + Ollama + H2)

This is a local-first Maven Spring Boot starter for the workflow you described:

1. Receive an incident with `eventDate` and `errorMessage`
2. Let the LLM call a diagnostic tool
3. Let the LLM call an on-call lookup tool
4. If restart is recommended, create an approval request and persist it in H2
5. Receive an approval response later
6. Validate the approval and restart the service if approved

## Stack

- Java 17
- Maven
- Spring Boot
- Spring AI with Ollama
- H2 in-memory database
- Spring Data JPA

## Project layout

- `AgentDriverService`: the agent/orchestrator
- `AgentTools`: tool methods exposed to the model with `@Tool`
- `DiagnosisService`, `OnCallService`, `ApprovalService`, `RestartService`: application services behind the tools
- `ApprovalEntity` + `ApprovalRepository`: local persistence for approval state
- `IncidentController` and `ApprovalController`: local REST endpoints for testing

## Prerequisites

- Java 17+
- Maven 3.6.3+
- Ollama running locally

## Start Ollama

Example:

```bash
ollama serve
ollama pull llama3.1:8b
```

If you want a different local model, set `OLLAMA_MODEL`.

## Run the app

```bash
mvn spring-boot:run
```

The app expects Ollama at `http://localhost:11434` by default.

Optional overrides:

```bash
export OLLAMA_BASE_URL=http://localhost:11434
export OLLAMA_MODEL=llama3.1:8b
```

## Test the incident flow

Send an incident:

```bash
curl -X POST http://localhost:8080/api/incidents \
  -H "Content-Type: application/json" \
  -d '{
    "eventDate": "2026-04-19T14:32:10Z",
    "errorMessage": "payments-api repeated 500 errors after deployment"
  }'
```

Typical result:

```json
{
  "eventType": "INCIDENT_DETECTED",
  "status": "AWAITING_APPROVAL",
  "summary": "Restart is recommended and an approval request was created for the on-call engineer.",
  "recommendedAction": "RESTART_SERVICE",
  "approvalRequired": true,
  "approvalStatus": "PENDING",
  "restartStatus": "NOT_STARTED"
}
```

The approval record is stored in the H2 database.

## Inspect H2 locally

Open:

- `http://localhost:8080/h2-console`

Use:

- JDBC URL: `jdbc:h2:mem:oncalldb`
- Username: `sa`
- Password: leave blank

Then run:

```sql
select * from approvals;
```

## Test the approval flow

Take the `approval_id` value from the `approvals` table and send:

```bash
curl -X POST http://localhost:8080/api/approvals/response \
  -H "Content-Type: application/json" \
  -d '{
    "approvalId": "PUT_APPROVAL_ID_HERE",
    "slackUserId": "U12345678",
    "response": "APPROVE_RESTART"
  }'
```

Typical result:

```json
{
  "eventType": "APPROVAL_RESPONSE",
  "status": "COMPLETED",
  "summary": "Approval was validated and the restart was triggered.",
  "recommendedAction": "RESTART_SERVICE",
  "approvalRequired": true,
  "approvalStatus": "APPROVED",
  "restartStatus": "RESTART_TRIGGERED"
}
```

## Important notes

- `OnCallService` is stubbed and always returns the same user.
- `DiagnosisService` is rule-based so you can validate the full flow locally.
- `ApprovalService` persists approval state in H2 so the second request can validate the first.
- `RestartService` is a stub. It does not call real infrastructure yet.
- `requestRestartApproval()` stores the approval request but only returns a message saying where your future Slack integration should go.

## Good next steps

1. Replace the stub `OnCallService` with a real schedule lookup
2. Replace the rule-based `DiagnosisService` if you want more deterministic diagnostics
3. Wire `requestRestartApproval()` to Slack
4. Replace `RestartService` with your real restart logic
5. Add a dedicated incident table if you want stronger audit history
