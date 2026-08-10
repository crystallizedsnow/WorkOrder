package api

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"

	"workorder-cli/internal/auth"

	"github.com/spf13/viper"
)

const (
	defaultTimeout = 30 * time.Second
)

type Client struct {
	httpClient    *http.Client
	backendURL    string
	cliServiceURL string
}

func NewClient() *Client {
	return &Client{
		httpClient: &http.Client{
			Timeout: defaultTimeout,
			Transport: &http.Transport{
				MaxIdleConns:        10,
				IdleConnTimeout:     30 * time.Second,
				TLSHandshakeTimeout: 10 * time.Second,
			},
		},
		backendURL:    viper.GetString("backend-url"),
		cliServiceURL: viper.GetString("cli-service-url"),
	}
}

type ApiResponse struct {
	Code    int         `json:"code"`
	Message string      `json:"message"`
	Msg     string      `json:"msg"`
	Data    interface{} `json:"data"`
	TraceId string      `json:"traceId"`
}

type LoginRequest struct {
	Phone    string `json:"phone"`
	Password string `json:"password"`
}

type LoginResponse struct {
	Code int    `json:"code"`
	Msg  string `json:"msg"`
	Data string `json:"data"`
}

type QueryRequest struct {
	DataCode string                 `json:"dataCode"`
	Params   map[string]interface{} `json:"params"`
}

func (c *Client) Login(ctx context.Context, phone, password string) (*LoginResponse, error) {
	url := fmt.Sprintf("%s/user/login", c.backendURL)
	reqBody := LoginRequest{Phone: phone, Password: password}

	data, err := json.Marshal(reqBody)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal login request: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewBuffer(data))
	if err != nil {
		return nil, fmt.Errorf("failed to create login request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("login request failed: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read login response: %w", err)
	}

	var loginResp LoginResponse
	if err := json.Unmarshal(body, &loginResp); err != nil {
		return nil, fmt.Errorf("failed to parse login response: %w", err)
	}

	return &loginResp, nil
}

func (c *Client) Get(ctx context.Context, url string, headers map[string]string) (*ApiResponse, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	for key, value := range headers {
		req.Header.Set(key, value)
	}

	return c.doRequest(req)
}

func (c *Client) Post(ctx context.Context, url string, body interface{}, headers map[string]string) (*ApiResponse, error) {
	var reqBody io.Reader
	if body != nil {
		data, err := json.Marshal(body)
		if err != nil {
			return nil, fmt.Errorf("failed to marshal request body: %w", err)
		}
		reqBody = bytes.NewBuffer(data)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, reqBody)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}

	for key, value := range headers {
		req.Header.Set(key, value)
	}

	return c.doRequest(req)
}

func (c *Client) Query(ctx context.Context, dataCode string, params map[string]interface{}, headers map[string]string) (*ApiResponse, error) {
	url := fmt.Sprintf("%s/api/query", c.cliServiceURL)

	reqBody := QueryRequest{
		DataCode: dataCode,
		Params:   params,
	}

	data, err := json.Marshal(reqBody)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal query request: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewBuffer(data))
	if err != nil {
		return nil, fmt.Errorf("failed to create query request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")

	for key, value := range headers {
		req.Header.Set(key, value)
	}

	return c.doRequest(req)
}

func (c *Client) Execute(ctx context.Context, dataCode string, params map[string]interface{}, headers map[string]string) (*ApiResponse, error) {
	url := fmt.Sprintf("%s/api/execute", c.cliServiceURL)

	reqBody := QueryRequest{
		DataCode: dataCode,
		Params:   params,
	}

	data, err := json.Marshal(reqBody)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal execute request: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewBuffer(data))
	if err != nil {
		return nil, fmt.Errorf("failed to create execute request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")

	for key, value := range headers {
		req.Header.Set(key, value)
	}

	return c.doRequest(req)
}

func (c *Client) doRequest(req *http.Request) (*ApiResponse, error) {
	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("request failed: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read response: %w", err)
	}

	if len(body) == 0 {
		return &ApiResponse{
			Code:    0,
			Message: "success",
			Data:    nil,
			TraceId: "",
		}, nil
	}

	if !json.Valid(body) {
		return &ApiResponse{
			Code:    0,
			Message: "success",
			Data:    string(body),
			TraceId: "",
		}, nil
	}

	var rawData map[string]interface{}
	if err := json.Unmarshal(body, &rawData); err != nil {
		return nil, fmt.Errorf("failed to parse response: %w", err)
	}

	if _, hasCode := rawData["code"]; hasCode {
		var apiResp ApiResponse
		if err := json.Unmarshal(body, &apiResp); err != nil {
			return nil, fmt.Errorf("failed to parse response: %w", err)
		}
		if apiResp.Message == "" && apiResp.Msg != "" {
			apiResp.Message = apiResp.Msg
		}
		if apiResp.Code == 1 {
			apiResp.Code = 0
			if apiResp.Message == "" {
				apiResp.Message = "success"
			}
		}
		return &apiResp, nil
	}

	return &ApiResponse{
		Code:    0,
		Message: "success",
		Data:    rawData,
		TraceId: "",
	}, nil
}

func GetAuthHeaders() map[string]string {
	token := auth.GetToken()
	headers := make(map[string]string)
	if token != "" {
		headers["Authorization"] = token
	}
	return headers
}
