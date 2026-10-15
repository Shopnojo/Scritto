# Scritto Backend

Node.js + TypeScript REST API for Scritto. The API is versioned under `/api/v1`.

## Requirements

- Node.js 22 or newer
- npm

## Local development

1. Copy `.env.example` to `.env`.
2. Install dependencies with `npm install`.
3. Start the development server with `npm run dev`.
4. Open `http://localhost:3000/api/v1/health`.

## Scripts

- `npm run dev` — run the API with watch mode
- `npm run build` — compile TypeScript into `dist/`
- `npm test` — run automated tests
- `npm start` — run the compiled server

## Security

- Never commit `.env` or real credentials.
- Secrets and privileged database credentials must remain server-side.
- Protected routes must enforce authentication and authorization in the API.
- Database access and production integrations will be added after their contracts are verified.
