import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import Login from './Login';
import reportWebVitals from './reportWebVitals';
import {
  createBrowserRouter,
  RouterProvider,
} from "react-router-dom";
import Home from './Home';
import Goals from './pages/Goals'; // Goals page
import Accounts from './pages/Accounts'; // Accounts page
import ProtectedRoute from './ProtectedRoute';

const root = ReactDOM.createRoot(document.getElementById('root'));

const router = createBrowserRouter([
  {
    path: "/",
    element: <Login />,
  },
  {
    path: "/home",
  element: <ProtectedRoute><Home /></ProtectedRoute>,
  },
  {
    path: "/goals", // Goals tracking page
  element: <ProtectedRoute><Goals /></ProtectedRoute>,
  },
  {
    path: "/accounts", // Accounts management page
  element: <ProtectedRoute><Accounts /></ProtectedRoute>,
  },
]);

root.render(
  <React.StrictMode>
    <RouterProvider router={router} />
  </React.StrictMode>
);

// If you want to start measuring performance in your app, pass a function
// to log results (for example: reportWebVitals(console.log))
// or send to an analytics endpoint. Learn more: https://bit.ly/CRA-vitals
reportWebVitals();
