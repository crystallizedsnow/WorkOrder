package auth

import (
	"encoding/json"
	"os"
	"path/filepath"
	"time"

	"github.com/spf13/viper"
)

const (
	tokenDirName  = ".workorder"
	tokenFileName = "token"
)

type TokenInfo struct {
	Token        string    `json:"token"`
	RefreshToken string    `json:"refreshToken"`
	Phone        string    `json:"phone"`
	ExpireTime   time.Time `json:"expireTime"`
	CreateTime   time.Time `json:"createTime"`
}

func GetTokenFilePath() string {
	homeDir, _ := os.UserHomeDir()
	return filepath.Join(homeDir, tokenDirName, tokenFileName)
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

func SaveToken(token, refreshToken, phone string, expiresAt time.Time) error {
	homeDir, err := os.UserHomeDir()
	if err != nil {
		return err
	}

	tokenDir := filepath.Join(homeDir, tokenDirName)
	if err := os.MkdirAll(tokenDir, 0700); err != nil {
		return err
	}

	tokenInfo := &TokenInfo{
		Token:        token,
		RefreshToken: refreshToken,
		Phone:        phone,
		ExpireTime:   expiresAt,
		CreateTime:   time.Now(),
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
