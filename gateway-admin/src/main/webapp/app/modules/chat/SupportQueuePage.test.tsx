import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import SupportQueuePage from './SupportQueuePage';
import { renderPage, useMockServer } from 'app/test/testUtils';

useMockServer();

describe('SupportQueuePage', () => {
  it('lists the WAITING conversations oldest first, with the member, the count and a page without total', async () => {
    renderPage(<SupportQueuePage />, '/support');
    const table = await screen.findByRole('table');
    const rows = within(table).getAllByRole('row').slice(1);
    expect(rows.map((r) => within(r).getAllByRole('link')[0].textContent)).toEqual(['Payment stuck on pending', 'Buyer opened a dispute']);
    expect(within(rows[0]).getByRole('link', { name: '#42' })).toHaveAttribute('href', '/users/42');
    expect(within(rows[0]).getByText('1 messages')).toBeInTheDocument();
    expect(within(rows[1]).getByRole('link', { name: 'ann-fixed-9' })).toHaveAttribute('href', '/announces/ann-fixed-9');
    expect(within(table).queryByText('Cannot change my e-mail')).not.toBeInTheDocument();
    expect(screen.getByText('Page 1 · 2 rows on this page')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Join' })).toHaveLength(2);
  });

  it('switches to another status through the URL and shows the staff who joined', async () => {
    renderPage(<SupportQueuePage />, '/support', '/support?status=OPEN');
    const table = await screen.findByRole('table');
    expect(within(table).getByText('Cannot change my e-mail')).toBeInTheDocument();
    expect(within(table).getByText('Staff #1')).toBeInTheDocument();
    expect(within(table).getByRole('link', { name: 'Open' })).toHaveAttribute('href', '/support/sup-88-c3');
    fireEvent.change(screen.getByLabelText('Status'), { target: { value: 'BLOCKED' } });
    expect(await within(screen.getByRole('table')).findByText('asdfgh')).toBeInTheDocument();
    expect(within(screen.getByRole('table')).getByText('Staff #2')).toBeInTheDocument();
  });

  it('join posts to the admin route and opens the conversation', async () => {
    renderPage(<SupportQueuePage />, '/support');
    const table = await screen.findByRole('table');
    fireEvent.click(within(table).getAllByRole('button', { name: 'Join' })[0]);
    await waitFor(() => expect(screen.getByTestId('elsewhere')).toBeInTheDocument()); // navigated to /support/sup-42-a1
  });
});
