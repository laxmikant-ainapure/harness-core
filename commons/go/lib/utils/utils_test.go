package utils

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
)

func Test_Ms(t *testing.T) {
	expected := float64(60000)
	assert.Equal(t, expected, Ms(time.Minute))

	expected = float64(1000)
	assert.Equal(t, expected, Ms(time.Second))
}

func Test_NoOp(t *testing.T) {
	assert.Equal(t, nil, NoOp())
}

func Test_ParseJavaNode(t *testing.T) {
	f := "320-ci-execution/src/main/java/io/harness/stateutils/buildstate/ConnectorUtils.java"

	node1 := Node{
		Pkg:   "io.harness.stateutils.buildstate",
		Class: "ConnectorUtils",
		Type:  NodeType_SOURCE,
		Lang:  LangType_JAVA,
	}
	node, _ := ParseJavaNode(f)
	assert.Equal(t, node1, *node, "extracted java node does not match")
}

func Test_ParseFileNames(t *testing.T) {

	files := []string{
		"320-ci-execution/src/main/java/io/harness/stateutils/buildstate/ConnectorUtils.java",
		"320-ci-execution/src/test/java/io/harness/stateutils/buildstate/TestConnectorUtils.java",
		"330-ci-beans/pom.xml",
		"320-ci-execution/src/main/java/io/harness/stateutils/buildstate/ConnectorUtils", //.java extension is missing

	}
	node1 := Node{
		Pkg:   "io.harness.stateutils.buildstate",
		Class: "ConnectorUtils",
		Type:  NodeType_SOURCE,
		Lang:  LangType_JAVA,
	}

	node2 := Node{
		Pkg:   "io.harness.stateutils.buildstate",
		Class: "TestConnectorUtils",
		Type:  NodeType_TEST,
		Lang:  LangType_JAVA,
	}

	unknown := Node{
		Pkg:   "",
		Class: "",
		Type:  NodeType_OTHER,
		Lang:  LangType_UNKNOWN,
	}

	nodes, _ := ParseFileNames(files)

	nodesExpected := []Node{node1, node2, unknown, unknown}

	assert.Equal(t, nodesExpected, nodes, "extracted nodes don't match")
}
