# Byawasaya frontend

React + TypeScript + Vite web application.

```bash
npm install
npm run dev
npm run build
npx eslint src --no-warn-ignored
```

The application expects the Spring Boot API at `http://localhost:8080` by default. Set `VITE_API_BASE_URL` in `frontend/.env.local` to override this in local development.

Routes: `/` platform landing, `/login`, `/register`, `/dashboard` (role-aware workspace), and `/store/{slug}` (public storefront).
