import { render, screen } from '@testing-library/react';
import Login from './Login';

// The app's root route ("/") renders the Login screen — there is no App component.
// This smoke test guards that the entry screen mounts and shows its primary actions.
test('renders the login screen', () => {
  render(<Login />);
  expect(screen.getByRole('heading', { name: /personal finance app/i })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /^login$/i })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /create account/i })).toBeInTheDocument();
});
