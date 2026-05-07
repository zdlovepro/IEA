# Interactive-Edu-Agent Frontend

Vue 3 + Vite frontend for the Interactive-Edu-Agent project.

## Structure

```text
src/
├── assets/        static assets and global styles
├── components/    reusable Vue components
├── constants/     frontend constants and API path helpers
├── router/        route definitions
├── stores/        Pinia stores
├── utils/         request helpers and utilities
├── views/         page-level views
├── App.vue        root component
└── main.js        app entry
```

## Quick Start

```bash
npm install
npm run dev
```

The dev server runs at `http://localhost:5173`.

## Build

```bash
npm run build
```

## Environment

Use [frontend/.env.example](/D:/205zd/Desktop/Interactive-Edu-Agent/frontend/.env.example) as the local sample:

```bash
cp .env.example .env.local
```

## Tech Stack

- Vue 3
- Vite
- Pinia
- Vue Router
- Axios
