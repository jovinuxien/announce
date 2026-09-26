import { fireEvent, render, screen } from '@testing-library/react';
import ModerationDialog, { validateReason } from './ModerationDialog';

describe('validateReason', () => {
  it('requires a non-blank reason of a sensible length', () => {
    expect(validateReason('')).toBe('announces.moderation.reasonRequired');
    expect(validateReason('   ')).toBe('announces.moderation.reasonRequired');
    expect(validateReason('ab')).toBe('announces.moderation.reasonTooShort');
    expect(validateReason('x'.repeat(501))).toBe('announces.moderation.reasonTooLong');
    expect(validateReason('Counterfeit goods')).toBeNull();
  });
});

describe('ModerationDialog', () => {
  it('does not submit without a reason and shows the validation message', () => {
    const onSubmit = jest.fn();
    render(<ModerationDialog show action="withdraw" announceTitle="Bike" onSubmit={onSubmit} onCancel={() => undefined} />);
    fireEvent.click(screen.getByRole('button', { name: 'Withdraw' }));
    expect(onSubmit).not.toHaveBeenCalled();
    expect(screen.getByText('A reason is required.')).toBeInTheDocument();
  });

  it('submits the trimmed reason', () => {
    const onSubmit = jest.fn();
    render(<ModerationDialog show action="expire" announceTitle="Bike" onSubmit={onSubmit} onCancel={() => undefined} />);
    fireEvent.change(screen.getByLabelText('Reason'), { target: { value: '  Listing outdated  ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Expire' }));
    expect(onSubmit).toHaveBeenCalledWith('Listing outdated');
    expect(screen.getByText('Expire announce')).toBeInTheDocument();
  });
});
