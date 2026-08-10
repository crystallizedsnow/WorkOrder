package auth

import (
	"encoding/json"
	"os"
	"path/filepath"
	"time"

	"github.com/spf13/viper"
)

const (
	tokenDirName    = ".workorder"
	tokenFileName   = "token"
	accountFileName = "account"
	tokenExpiryDays = 7
)

type TokenInfo struct {
	Token      string    `json:"token"`
	Phone      string    `json:"phone"`
	ExpireTime time.Time `json:"expireTime"`
	CreateTime time.Time `json:"createTime"`
}

// AccountInfo 保存在本地 account 文件中的账号凭证，
// 用于 Token 过期后自动重新登录（无需 Agent 读取文件或用户重新输入）。
type AccountInfo struct {
	Phone    string `json:"phone"`
	Password string `json:"password"`
}

func GetTokenFilePath() string {
	homeDir, _ := os.UserHomeDir()
	return filepath.Join(homeDir, tokenDirName, tokenFileName)
}

func GetAccountFilePath() string {
	homeDir, _ := os.UserHomeDir()
	return filepath.Join(homeDir, tokenDirName, accountFileName)
}

func GetToken() string {
	if token := viper.GetString("token"); token != "" {
		return token
	}

	if token := os.Getenv("WORKORDER_TOKEN"); token != "" {
		return token
	}

	tokenInfo, err := loadTokenFromFile()
	if err != nil || tokenInfo == nil || isTokenExpired(tokenInfo) {
		return ""
	}

	return tokenInfo.Token
}

func SaveToken(token, phone string) error {
	homeDir, err := os.UserHomeDir()
	if err != nil {
		return err
	}

	tokenDir := filepath.Join(homeDir, tokenDirName)
	if err := os.MkdirAll(tokenDir, 0700); err != nil {
		return err
	}

	tokenInfo := &TokenInfo{
		Token:      token,
		Phone:      phone,
		ExpireTime: time.Now().Add(tokenExpiryDays * 24 * time.Hour),
		CreateTime: time.Now(),
	}

	data, err := json.MarshalIndent(tokenInfo, "", "  ")
	if err != nil {
		return err
	}

	tokenFile := filepath.Join(tokenDir, tokenFileName)
	return os.WriteFile(tokenFile, data, 0600)
}

func DeleteToken() error {
	tokenFile := GetTokenFilePath()
	if _, err := os.Stat(tokenFile); os.IsNotExist(err) {
		return nil
	}
	return os.Remove(tokenFile)
}

func GetTokenInfo() (*TokenInfo, error) {
	return loadTokenFromFile()
}

func loadTokenFromFile() (*TokenInfo, error) {
	tokenFile := GetTokenFilePath()
	data, err := os.ReadFile(tokenFile)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, nil
		}
		return nil, err
	}

	var tokenInfo TokenInfo
	if err := json.Unmarshal(data, &tokenInfo); err != nil {
		return nil, err
	}

	return &tokenInfo, nil
}

func isTokenExpired(tokenInfo *TokenInfo) bool {
	return tokenInfo.ExpireTime.IsZero() || time.Now().After(tokenInfo.ExpireTime)
}

func IsTokenExpired(tokenInfo *TokenInfo) bool {
	return isTokenExpired(tokenInfo)
}

// SaveAccount 将账号凭证（手机号+密码）保存到本地 account 文件，
// 供后续 Token 过期时 auth login 无参数自动读取重登。
func SaveAccount(phone, password string) error {
	homeDir, err := os.UserHomeDir()
	if err != nil {
		return err
	}

	accountDir := filepath.Join(homeDir, tokenDirName)
	if err := os.MkdirAll(accountDir, 0700); err != nil {
		return err
	}

	account := &AccountInfo{
		Phone:    phone,
		Password: password,
	}

	data, err := json.MarshalIndent(account, "", "  ")
	if err != nil {
		return err
	}

	accountFile := filepath.Join(accountDir, accountFileName)
	return os.WriteFile(accountFile, data, 0600)
}

// LoadAccount 从本地 account 文件读取账号凭证。
// 文件不存在时返回 (nil, nil)，由调用方决定后续处理。
func LoadAccount() (*AccountInfo, error) {
	accountFile := GetAccountFilePath()
	data, err := os.ReadFile(accountFile)
	if err != nil {
		if os.IsNotExist(err) {
			return nil, nil
		}
		return nil, err
	}

	var account AccountInfo
	if err := json.Unmarshal(data, &account); err != nil {
		return nil, err
	}

	return &account, nil
}
