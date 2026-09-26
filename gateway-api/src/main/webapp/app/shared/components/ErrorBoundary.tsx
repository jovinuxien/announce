import { Component, ErrorInfo, ReactNode } from 'react';
import { Alert } from 'react-bootstrap';
import { withTranslation, WithTranslation } from 'react-i18next';

interface Props extends WithTranslation { children: ReactNode }
interface State { error: Error | null }

class ErrorBoundaryInner extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error('render error', error, info.componentStack);
  }

  render() {
    if (this.state.error) {
      return (
        <Alert variant="danger" className="m-3">
          <Alert.Heading>{this.props.t('errors:renderFailed')}</Alert.Heading>
          <p className="mb-0">{this.state.error.message}</p>
        </Alert>
      );
    }
    return this.props.children;
  }
}

export default withTranslation()(ErrorBoundaryInner);
