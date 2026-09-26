import { Alert } from 'react-bootstrap';

export default function ProblemAlert({ message, onClose }: { message: string | null | undefined; onClose?: () => void }) {
  if (!message) return null;
  return (
    <Alert variant="danger" dismissible={!!onClose} onClose={onClose} className="my-2">
      {message}
    </Alert>
  );
}
