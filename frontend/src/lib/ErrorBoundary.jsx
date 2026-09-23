// Without this, an uncaught render error anywhere in the tree unmounts the whole app - React's default
// behaviour with no error boundary. Since `body` has its own CSS background-image (game.css), the only
// thing left on screen is that background: everything React-rendered (chrome, content, all of it) vanishes.
// That silent full-page blank-out was the actual bug behind "everything except the background disappears".
import { Component } from "react";

export class ErrorBoundary extends Component {
  state = { error: null, resetKey: undefined };

  static getDerivedStateFromError(error) {
    return { error };
  }

  // navigating away (a new resetKey) clears a screen-level crash so the user isn't stuck
  static getDerivedStateFromProps(props, state) {
    return state.resetKey === props.resetKey ? null : { error: null, resetKey: props.resetKey };
  }

  componentDidCatch(error, info) {
    console.error("Render crashed, caught by ErrorBoundary:", error, info.componentStack);
  }

  render() {
    if (!this.state.error) return this.props.children;
    if (this.props.fallback) return this.props.fallback(this.state.error);
    return (
      <div className="error_box tw-error">
        Something went wrong showing this page ({this.state.error.message || "unknown error"}).{" "}
        <a
          href="#"
          onClick={(e) => {
            e.preventDefault();
            this.setState({ error: null });
          }}
        >
          Try again
        </a>
        .
      </div>
    );
  }
}
