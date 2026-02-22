import React, { useState, useRef, useEffect } from 'react';

interface Message {
  text: string;
  sender: 'user' | 'bot';
}

const ChatBot: React.FC = () => {
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  const sendMessage = async () => {
    if (!input.trim()) return;

    const userMessage: Message = { text: input, sender: 'user' };
    setMessages((prev) => [...prev, userMessage]);
    setInput('');
    setIsLoading(true);

    try {
      const res = await fetch(`http://localhost:8081/ask?message=${encodeURIComponent(input)}`);
      const data = await res.text();

      const botMessage: Message = { text: data, sender: 'bot' };
      setMessages((prev) => [...prev, botMessage]);
    } catch (error) {
      const errorMessage: Message = {
        text: 'Error connecting to the server. Please make sure the backend is running.',
        sender: 'bot',
      };
      setMessages((prev) => [...prev, errorMessage]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      sendMessage();
    }
  };

  return (
    <div style={styles.container}>
      <div style={styles.header}>
        <h1 style={styles.title}>Spring AI Chatbot</h1>
        <p style={styles.subtitle}>Ask about strategy fees status</p>
      </div>

      <div style={styles.chatContainer}>
        <div style={styles.messagesContainer}>
          {messages.length === 0 && (
            <div style={styles.welcomeMessage}>
              <p>Hello! I can help you check fees status for strategies.</p>
              <p>Try asking: "What is the fees status for strategy code STR001?"</p>
            </div>
          )}
          {messages.map((msg, index) => (
            <div
              key={index}
              style={{
                ...styles.message,
                ...(msg.sender === 'user' ? styles.userMessage : styles.botMessage),
              }}
            >
              {msg.text}
            </div>
          ))}
          {isLoading && (
            <div style={{ ...styles.message, ...styles.botMessage }}>
              <span style={styles.typing}>Thinking...</span>
            </div>
          )}
          <div ref={messagesEndRef} />
        </div>

        <div style={styles.inputContainer}>
          <textarea
            style={styles.input}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyPress={handleKeyPress}
            placeholder="Type your message..."
            rows={2}
            disabled={isLoading}
          />
          <button
            style={{ ...styles.sendButton, ...(isLoading ? styles.disabledButton : {}) }}
            onClick={sendMessage}
            disabled={isLoading}
          >
            Send
          </button>
        </div>
      </div>
    </div>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  container: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    minHeight: '100vh',
    backgroundColor: '#f5f5f5',
    padding: '20px',
  },
  header: {
    textAlign: 'center',
    marginBottom: '20px',
  },
  title: {
    color: '#333',
    marginBottom: '5px',
  },
  subtitle: {
    color: '#666',
    fontSize: '14px',
  },
  chatContainer: {
    width: '100%',
    maxWidth: '600px',
    backgroundColor: 'white',
    borderRadius: '10px',
    boxShadow: '0 2px 10px rgba(0,0,0,0.1)',
    overflow: 'hidden',
    display: 'flex',
    flexDirection: 'column',
    height: '70vh',
  },
  messagesContainer: {
    flex: 1,
    overflowY: 'auto',
    padding: '20px',
  },
  message: {
    padding: '10px 15px',
    borderRadius: '15px',
    marginBottom: '10px',
    maxWidth: '80%',
    wordWrap: 'break-word',
    whiteSpace: 'pre-wrap',
  },
  userMessage: {
    backgroundColor: '#007bff',
    color: 'white',
    marginLeft: 'auto',
    textAlign: 'right',
  },
  botMessage: {
    backgroundColor: '#e9ecef',
    color: '#333',
    marginRight: 'auto',
  },
  welcomeMessage: {
    textAlign: 'center',
    color: '#666',
    padding: '20px',
  },
  inputContainer: {
    display: 'flex',
    padding: '15px',
    borderTop: '1px solid #eee',
    gap: '10px',
  },
  input: {
    flex: 1,
    padding: '10px 15px',
    borderRadius: '20px',
    border: '1px solid #ddd',
    fontSize: '14px',
    resize: 'none',
    outline: 'none',
    fontFamily: 'inherit',
  },
  sendButton: {
    padding: '10px 25px',
    backgroundColor: '#007bff',
    color: 'white',
    border: 'none',
    borderRadius: '20px',
    cursor: 'pointer',
    fontSize: '14px',
    fontWeight: 'bold',
  },
  disabledButton: {
    backgroundColor: '#ccc',
    cursor: 'not-allowed',
  },
  typing: {
    fontStyle: 'italic',
    color: '#666',
  },
};

export default ChatBot;